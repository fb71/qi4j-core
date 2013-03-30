/*  Copyright 2007 Niclas Hedhman.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 * 
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.qi4j.runtime.unitofwork;

import static org.qi4j.api.unitofwork.UnitOfWorkCallback.UnitOfWorkStatus.COMPLETED;
import static org.qi4j.api.unitofwork.UnitOfWorkCallback.UnitOfWorkStatus.DISCARDED;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import org.qi4j.api.common.TypeName;
import org.qi4j.api.composite.AmbiguousTypeException;
import org.qi4j.api.entity.EntityComposite;
import org.qi4j.api.entity.EntityReference;
import org.qi4j.api.unitofwork.ConcurrentEntityModificationException;
import org.qi4j.api.unitofwork.EntityTypeNotFoundException;
import org.qi4j.api.unitofwork.NoSuchEntityException;
import org.qi4j.api.unitofwork.UnitOfWorkCallback;
import org.qi4j.api.unitofwork.UnitOfWorkCompletionException;
import org.qi4j.api.unitofwork.UnitOfWorkException;
import org.qi4j.api.usecase.Usecase;
import org.qi4j.runtime.entity.EntityInstance;
import org.qi4j.runtime.entity.EntityModel;
import org.qi4j.runtime.structure.ModuleInstance;
import org.qi4j.runtime.structure.ModuleUnitOfWork;
import org.qi4j.spi.entity.EntityState;
import org.qi4j.spi.entity.EntityStatus;
import org.qi4j.spi.entitystore.ConcurrentEntityStateModificationException;
import org.qi4j.spi.entitystore.EntityNotFoundException;
import org.qi4j.spi.entitystore.EntityStore;
import org.qi4j.spi.entitystore.EntityStoreUnitOfWork;
import org.qi4j.spi.entitystore.StateCommitter;
import org.qi4j.spi.structure.ModuleSPI;

import org.polymap.core.runtime.cache.Cache;
import org.polymap.core.runtime.cache.CacheConfig;
import org.polymap.core.runtime.cache.CacheManager;

public final class UnitOfWorkInstance
{
    // FIXME fb71: this causes mem leaks if work and commit is done in different threads;
    // as I don't need pause/resume I'm disabling this completely; to get this working
    // properly thread/uow mapping has to be checked when accessing uow; on the other hand
    // this would not allow to access uow from different threads; I don't see a reason for
    // this; UnitOfWorkInstance needs to be thread safe and thats it
     //public static final ThreadLocal<Stack<UnitOfWorkInstance>> current;

    // XXX _fb71: make this concurrent in order to allow concurrent access to
    // an UnitOfWork
    final Cache<EntityReference, EntityState> stateCache;
    final Cache<InstanceKey, EntityInstance> instanceCache;
    final HashMap<EntityStore, EntityStoreUnitOfWork> storeUnitOfWork;

    private boolean open;

    private boolean paused;

    /**
     * Lazy query builder factory.
     */
    private Usecase usecase;

    private List<UnitOfWorkCallback> callbacks;

    static
    {
// FIXME fb71
//        current = new ThreadLocal<Stack<UnitOfWorkInstance>>()
//        {
//            protected Stack<UnitOfWorkInstance> initialValue()
//            {
//                return new Stack<UnitOfWorkInstance>();
//            }
//        };
    }

    public UnitOfWorkInstance( Usecase usecase )
    {
        this.open = true;

        // soft references for caching introduce major issues, however, they are currently
        // the only possible solution; using the polymap cache results in different instances
        // of the same entity are flying around; this is not what a programmer would expect;
        // with soft references an entity is reclamied when *no* party is using it. 
        
//        stateCache = new FakeConcurrentMap( 1024, 0.75f );
//        instanceCache = new FakeConcurrentMap( 1024, 0.75f );
        
//        stateCache = new CacheBuilder().softValues().initialCapacity( 1024 ).concurrencyLevel( 4 ).build( null );
        
//        stateCache = new MapMaker().initialCapacity( 1024 ).softValues().concurrencyLevel( 8 ).makeMap();
//        instanceCache = new MapMaker().initialCapacity( 1024 ).softValues().concurrencyLevel( 8 ).makeMap();

//        stateCache = new ConcurrentHashMap( 1024, 0.75f, 8 );
//        instanceCache = new ConcurrentHashMap( 1024, 0.75f, 8 );
        
//        stateCache = new ConcurrentReferenceHashMap( 1024, 0.75f, 16, ReferenceType.STRONG, ReferenceType.SOFT, null );
//        instanceCache = new ConcurrentReferenceHashMap( 1024, 0.75f, 16, ReferenceType.STRONG, ReferenceType.SOFT, null );

        stateCache = CacheManager.instance().newCache( CacheConfig.DEFAULT );
        instanceCache = CacheManager.instance().newCache( CacheConfig.DEFAULT );
        
        storeUnitOfWork = new HashMap<EntityStore, EntityStoreUnitOfWork>();
//        current.get().push( this );
        paused = false;
        this.usecase = usecase;
    }

    public EntityStoreUnitOfWork getEntityStoreUnitOfWork( EntityStore store, ModuleSPI module )
    {
        EntityStoreUnitOfWork uow = storeUnitOfWork.get( store );
        if( uow == null )
        {
            uow = store.newUnitOfWork( usecase, module );
            storeUnitOfWork.put( store, uow );
        }
        return uow;
    }

    /** 
     * XXX fb71: HACK: allow {@link EntityQuery} access to this. I don't see to proper
     * way to do this currently
     */
    public EntityStoreUnitOfWork entityStoreUnitOfWork() {
        assert storeUnitOfWork.size() <= 1;
        return storeUnitOfWork.isEmpty() ? null : storeUnitOfWork.values().iterator().next();
    }
    
    public EntityInstance get( EntityReference identity,
                               ModuleUnitOfWork uow,
                               List<EntityModel> potentialModels,
                               List<ModuleInstance> potentialModules,
                               Class mixinType
    )
        throws EntityTypeNotFoundException, NoSuchEntityException
    {
        checkOpen();

        EntityState entityState = stateCache.get( identity );
        EntityInstance entityInstance;
        if( entityState == null )
        {   // Not yet in cache

            // Check if this is a root UoW, or if no parent UoW knows about this entity
            EntityModel model = null;
            ModuleInstance module = null;
            // Figure out what EntityStore to use
            for( ModuleInstance potentialModule : potentialModules )
            {
                EntityStore store = potentialModule.entities().entityStore();
                EntityStoreUnitOfWork storeUow = getEntityStoreUnitOfWork( store, potentialModule );
                try
                {
                    entityState = storeUow.getEntityState( identity );
                }
                catch( EntityNotFoundException e )
                {
                    continue;
                }

                // Get the selected model
                model = (EntityModel) entityState.entityDescriptor();
                module = potentialModule;
            }

            // Check if model was found
            if( model == null )
            {
                // Check if state was found
                if( entityState == null )
                {
                    throw new NoSuchEntityException( identity );
                }
                else
                {
                    throw new EntityTypeNotFoundException( mixinType.getName() );
                }
            }

            // Create instance
            entityInstance = new EntityInstance( uow, module, model, entityState );

            // fb71: another thread might have instantiated this entity; check and use
            // previous if there is one and discard my entityState
            EntityState previousState = stateCache.putIfAbsent( identity, entityState );
            if (previousState != null) {
                // let the other thread do the work in the next 100ms;
                // avoid subsequent concurrent loads
                try {
                    System.out.println( "UoW: sleeping while waiting for entity to be instantiated..." );
                    Thread.sleep( 200 );
                }
                catch (InterruptedException e) {
                }
                entityState = previousState;
            }
            
            // see above
            InstanceKey instanceKey = new InstanceKey( model.entityType().type(), identity );
            EntityInstance previousInstance = instanceCache.putIfAbsent( instanceKey, entityInstance );
            entityInstance = previousInstance != null ? previousInstance : entityInstance;
        }
        else
        {
            // Check if it has been removed
            if( entityState.status() == EntityStatus.REMOVED )
            {
                throw new NoSuchEntityException( identity );
            }

            // Find instance in cache
            InstanceKey instanceKey = new InstanceKey();
            for( EntityModel potentialModel : potentialModels )
            {
                instanceKey.update( potentialModel.entityType().type(), identity );
                EntityInstance instance = instanceCache.get( instanceKey );
                if( instance != null )
                {
                    return instance; // Found it!
                }
            }

            // State is in UoW, but no model for this mixin type has been used before
            // See if any types match
            EntityModel model = null;
            ModuleInstance module = null;
            for( int i = 0; i < potentialModels.size(); i++ )
            {
                EntityModel potentialModel = potentialModels.get( i );
                TypeName typeRef = potentialModel.entityType().type();
                if( entityState.isOfType( typeRef ) )
                {
                    // Found it!
                    // Check for ambiguity
                    if( model != null )
                    {
                        throw new AmbiguousTypeException( mixinType, model.type(), potentialModel.type() );
                    }

                    model = potentialModel;
                    module = potentialModules.get( i );
                }
            }

            // Create instance
            entityInstance = new EntityInstance( uow, module, model, entityState );

            instanceKey.update( model.entityType().type(), identity );
            // see above
            EntityInstance previousInstance = instanceCache.putIfAbsent( instanceKey, entityInstance );
            entityInstance = previousInstance != null ? previousInstance : entityInstance;
        }

        return entityInstance;
    }

    public Usecase usecase()
    {
        return usecase;
    }

    public void pause()
    {
        if( !paused )
        {
            paused = true;
// FIXME fb71
//            current.get().pop();
        }
        else
        {
            throw new UnitOfWorkException( "Unit of work is not active" );
        }
    }

    public void resume()
    {
        if( paused )
        {
            paused = false;
// FIXME fb71
//            current.get().push( this );
        }
        else
        {
            throw new UnitOfWorkException( "Unit of work has not been paused" );
        }
    }

    public void complete()
        throws UnitOfWorkCompletionException
    {
        complete( false );
    }

    public void apply()
        throws UnitOfWorkCompletionException, ConcurrentEntityModificationException
    {
        complete( true );
    }

    private void complete( boolean completeAndContinue )
        throws UnitOfWorkCompletionException
    {
        checkOpen();

        // Copy list so that it cannot be modified during completion
        List<UnitOfWorkCallback> currentCallbacks = callbacks == null ? null : new ArrayList<UnitOfWorkCallback>( callbacks );

        // Check callbacks
        notifyBeforeCompletion( currentCallbacks );

        // Commit state to EntityStores
        List<StateCommitter> committers = applyChanges();

        // Commit all changes
        for( StateCommitter committer : committers )
        {
            committer.commit();
        }

        if( completeAndContinue )
        {
            continueWithState();
        }
        else
        {
            close();
        }

        // Call callbacks
        notifyAfterCompletion( currentCallbacks, COMPLETED );

        callbacks = currentCallbacks;
    }

    public void discard()
    {
        if( !isOpen() )
        {
            return;
        }
        close();

        // Copy list so that it cannot be modified during completion
        List<UnitOfWorkCallback> currentCallbacks = callbacks == null ? null : new ArrayList<UnitOfWorkCallback>( callbacks );

        // Call callbacks
        notifyAfterCompletion( currentCallbacks, DISCARDED );

        for( EntityStoreUnitOfWork entityStoreUnitOfWork : storeUnitOfWork.values() )
        {
            entityStoreUnitOfWork.discard();
        }

        callbacks = currentCallbacks;
    }

    protected void finalize() throws Throwable {
        try {
            close();
            if (stateCache instanceof Cache) {
                ((Cache)stateCache).dispose();
            }
            if (instanceCache instanceof Cache) {
                ((Cache)instanceCache).dispose();
            }
        }
        catch (Exception e) {
        }
    }

    private void close()
    {
        checkOpen();

// FIXME fb71
//        if( !isPaused() )
//        {
//            current.get().pop();
//        }
        open = false;

        for( EntityInstance entityInstance : instanceCache.values() )
        {
            entityInstance.discard();
        }

        stateCache.clear();
        instanceCache.clear();
    }

    public boolean isOpen()
    {
        return open;
    }

    public void addUnitOfWorkCallback( UnitOfWorkCallback callback )
    {
        if( callbacks == null )
        {
            callbacks = new ArrayList<UnitOfWorkCallback>();
        }

        callbacks.add( callback );
    }

    public void removeUnitOfWorkCallback( UnitOfWorkCallback callback )
    {
        if( callbacks != null )
        {
            callbacks.remove( callback );
        }
    }

    public void createEntity( EntityInstance instance )
    {
        stateCache.putIfAbsent( instance.identity(), instance.entityState() );
        InstanceKey instanceKey = new InstanceKey( instance.entityModel().entityType().type(), instance.identity() );
        instanceCache.putIfAbsent( instanceKey, instance );
    }

    private List<StateCommitter> applyChanges()
        throws UnitOfWorkCompletionException
    {
        List<StateCommitter> committers = new ArrayList<StateCommitter>();
        for( EntityStoreUnitOfWork entityStoreUnitOfWork : storeUnitOfWork.values() )
        {
            try
            {
                StateCommitter committer = entityStoreUnitOfWork.apply();
                committers.add( committer );
            }
            catch( Exception e )
            {
                // Cancel all previously prepared stores
                for( StateCommitter committer : committers )
                {
                    committer.cancel();
                }

                if( e instanceof ConcurrentEntityStateModificationException )
                {
                    // If we cancelled due to concurrent modification, then create the proper exception for it!
                    ConcurrentEntityStateModificationException mee = (ConcurrentEntityStateModificationException) e;
                    Collection<EntityReference> modifiedEntityIdentities = mee.modifiedEntities();
                    Collection<EntityComposite> modifiedEntities = new ArrayList<EntityComposite>();
                    for( EntityReference modifiedEntityIdentity : modifiedEntityIdentities )
                    {
                        for( EntityInstance instance : instanceCache.values() )
                        {
                            if( instance.identity().equals( modifiedEntityIdentity ) )
                            {
                                modifiedEntities.add( instance.<EntityComposite>proxy() );
                            }
                        }
                    }
                    throw new ConcurrentEntityModificationException( modifiedEntities );
                }
                else
                {
                    throw new UnitOfWorkCompletionException( e );
                }
            }
        }
        return committers;
    }

    private void continueWithState()
    {
        Iterator<EntityInstance> entityInstances = instanceCache.values().iterator();
        while( entityInstances.hasNext() )
        {
            EntityInstance entityInstance = entityInstances.next();
            if( entityInstance.status() == EntityStatus.REMOVED )
            {
                entityInstances.remove();
            }
        }

        Iterator<EntityState> stateStores = stateCache.values().iterator();
        while( stateStores.hasNext() )
        {
            EntityState entityState = stateStores.next();
            if( entityState.status() != EntityStatus.REMOVED )
            {
                entityState.hasBeenApplied();
            }
            else
            {
                stateStores.remove();
            }
        }
    }

    private void notifyBeforeCompletion( List<UnitOfWorkCallback> callbacks )
        throws UnitOfWorkCompletionException
    {
        // Notify explicitly registered callbacks
        if( callbacks != null )
        {
            for( UnitOfWorkCallback callback : callbacks )
            {
                callback.beforeCompletion();
            }
        }

        // Notify entities
        try
        {
            new ForEachEntity()
            {
                protected void execute( EntityInstance instance )
                    throws Exception
                {
                    
                    // XXX falko: removed entities should and cannot be called back
                    if (instance.entityState().status() != EntityStatus.REMOVED) 
                    {
                        if( instance.<Object>proxy() instanceof UnitOfWorkCallback )
                        {
                            UnitOfWorkCallback callback = UnitOfWorkCallback.class.cast( instance.proxy() );
                            callback.beforeCompletion();
                        }
                    }
                }
            }.execute();
        }
        catch( UnitOfWorkCompletionException e )
        {
            throw e;
        }
        catch( Exception e )
        {
            throw new UnitOfWorkCompletionException( e );
        }
    }

    private void notifyAfterCompletion( List<UnitOfWorkCallback> callbacks,
                                        final UnitOfWorkCallback.UnitOfWorkStatus status
    )
    {
        if( callbacks != null )
        {
            for( UnitOfWorkCallback callback : callbacks )
            {
                try
                {
                    callback.afterCompletion( status );
                }
                catch( Exception e )
                {
                    // Ignore
                }
            }
        }

        // Notify entities
        try
        {
            new ForEachEntity()
            {
                protected void execute( EntityInstance instance )
                    throws Exception
                {
                    if( instance.<Object>proxy() instanceof UnitOfWorkCallback )
                    {
                        UnitOfWorkCallback callback = UnitOfWorkCallback.class.cast( instance.proxy() );
                        callback.afterCompletion( status );
                    }
                }
            }.execute();
        }
        catch( Exception e )
        {
            // Ignore
        }
    }

    EntityState getCachedState( EntityReference entityId )
    {
        return stateCache.get( entityId );
    }

    public void checkOpen()
    {
        if( !isOpen() )
        {
            throw new UnitOfWorkException( "Unit of work has been closed" );
        }
    }

    public boolean isPaused()
    {
        return paused;
    }

    @Override
    public String toString()
    {
        return "UnitOfWork " + hashCode() + "(" + usecase + "): entities:" + stateCache.size();
    }

    public void remove( EntityReference entityReference )
    {
        stateCache.remove( entityReference );
    }

    abstract class ForEachEntity
    {
        void execute()
            throws Exception
        {
            for( EntityInstance entityInstance : instanceCache.values() )
            {
                execute( entityInstance );
            }
        }

        protected abstract void execute( EntityInstance instance )
            throws Exception;
    }

    private static class InstanceKey
    {
        private TypeName typeName;
        private EntityReference entityReference;

        private InstanceKey()
        {
        }

        private InstanceKey( TypeName typeName, EntityReference entityReference )
        {
            this.typeName = typeName;
            this.entityReference = entityReference;
        }

        public TypeName typeName()
        {
            return typeName;
        }

        public EntityReference entityReference()
        {
            return entityReference;
        }

        public void update( TypeName typeName, EntityReference entityReference )
        {
            this.typeName = typeName;
            this.entityReference = entityReference;
        }

        @Override
        public boolean equals( Object o )
        {
            if( this == o )
            {
                return true;
            }
            if( o == null || getClass() != o.getClass() )
            {
                return false;
            }

            InstanceKey that = (InstanceKey) o;
            if( !entityReference.equals( that.entityReference ) )
            {
                return false;
            }
            return typeName.equals( that.typeName );
        }

        @Override
        public int hashCode()
        {
            int result = typeName.hashCode();
            result = 31 * result + entityReference.hashCode();
            return result;
        }
    }
}
