/*
 * Copyright 2007-2009 Niclas Hedhman.
 * Copyright 2008 Alin Dreghiciu.
 *
 * Licensed  under the  Apache License,  Version 2.0  (the "License");
 * you may not use  this file  except in  compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed  under the  License is distributed on an "AS IS" BASIS,
 * WITHOUT  WARRANTIES OR CONDITIONS  OF ANY KIND, either  express  or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.qi4j.runtime.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.qi4j.api.common.TypeName;
import org.qi4j.api.entity.EntityComposite;
import org.qi4j.api.entity.EntityReference;
import org.qi4j.api.query.Query;
import org.qi4j.api.query.QueryExecutionException;
import org.qi4j.api.query.grammar.BooleanExpression;
import org.qi4j.api.unitofwork.UnitOfWork;
import org.qi4j.runtime.structure.ModuleUnitOfWork;
import org.qi4j.runtime.unitofwork.UnitOfWorkInstance;
import org.qi4j.spi.entity.EntityState;
import org.qi4j.spi.entity.EntityStatus;
import org.qi4j.spi.entitystore.EntityStoreUnitOfWork;
import org.qi4j.spi.entitystore.EntityStoreUnitOfWork2;
import org.qi4j.spi.query.EntityFinder;
import org.qi4j.spi.query.EntityFinder.PostProcessEntityReference;
import org.qi4j.spi.query.EntityFinderException;

import com.google.common.collect.Iterators;

/**
 * Default implementation of {@link Query}.
 */
final class EntityQuery<T>
    extends AbstractQuery<T>
{
    private static final long serialVersionUID = 1L;
    
    /**
     * Parent unit of work.
     */
    private final UnitOfWork unitOfWorkInstance;

    /**
     * Entity finder to be used to locate entities.
     */
    private final EntityFinder entityFinder;

    /**
     * Constructor.
     *
     * @param unitOfWorkInstance parent unit of work; cannot be null
     * @param entityFinder       entity finder to be used to locate entities; cannot be null
     * @param resultType         type of queried entities; cannot be null
     * @param whereClause        where clause
     */
    EntityQuery( final UnitOfWork unitOfWorkInstance,
                 final EntityFinder entityFinder,
                 final Class<T> resultType,
                 final BooleanExpression whereClause
    )
    {
        super( resultType, whereClause );
        this.unitOfWorkInstance = unitOfWorkInstance;
        this.entityFinder = entityFinder;
    }

    /**
     * @see Query#find()
     */
    public T find()
    {
        return Iterators.getOnlyElement( iterator(), null );
//        try
//        {
//            final EntityReference foundEntity = entityFinder.findEntity( resultType.getName(), whereClause );
//            if( foundEntity != null )
//            {
//                try
//                {
//                    return unitOfWorkInstance.get( resultType, foundEntity.identity() );
//                }
//                catch( NoSuchEntityException e )
//                {
//                    return null; // Index is out of sync - entity has been removed
//                }
//            }
//            // No entity was found
//            return null;
//        }
//        catch( EntityFinderException e )
//        {
//            throw new QueryExecutionException( "Finder caused exception", e );
//        }
    }

    /**
     * @see Query#iterator()
     */
    public Iterator<T> iterator()
        throws QueryExecutionException
    {
        try
        {
            final Iterator<EntityReference> foundEntities = entityFinder.findEntities( resultType.getName(),
                                                                                       whereClause,
                                                                                       orderBySegments,
                                                                                       firstResult,
                                                                                       maxResults ).iterator();
            UnitOfWorkInstance uow = ((ModuleUnitOfWork)unitOfWorkInstance).instance();
            EntityStoreUnitOfWork suow = uow.entityStoreUnitOfWork();
            final Map<String,EntityState> modifiedStates = suow != null && suow instanceof EntityStoreUnitOfWork2
                    ? ((EntityStoreUnitOfWork2)suow).modified() : Collections.EMPTY_MAP;
            
            // new/updated states
            List<T> updated = new ArrayList();
            for (EntityState entityState : modifiedStates.values()) {
                
                if (entityState.isOfType( TypeName.nameOf( resultType ) ) &&
                        (  entityState.status() == EntityStatus.NEW
                        || entityState.status() == EntityStatus.UPDATED)) {
                    
                    T entity = ((ModuleUnitOfWork)unitOfWorkInstance).get( resultType, entityState.identity() );
                    if (whereClause.eval( entity )) {
                        updated.add( entity );
                    }
                }
            }
            
            // queried states
            Iterator<T> queried = new Iterator<T>()
            {
                private T       next;
                
                public boolean hasNext()
                {
                    while (next == null && foundEntities.hasNext())
                    {
                        EntityReference identity = foundEntities.next();
        
                        // skip all updated entities
                        if (!modifiedStates.containsKey( identity.identity() )) {
                            // post process
                            T entity = ((ModuleUnitOfWork)unitOfWorkInstance).get( resultType, identity );
                            if (identity instanceof PostProcessEntityReference) {
                                PostProcessEntityReference postProcess = (PostProcessEntityReference)identity;
                                if (postProcess.apply( (EntityComposite)entity )) {
                                    next = entity;
                                }
                            }
                            else {
                                next = entity;
                            }
                        }
                    }
                    return next != null;
                }

                public T next()
                {
                    assert next != null;
                    try { return next; } finally { next = null; }
                }

                public void remove()
                {
                    throw new UnsupportedOperationException();
                }
            };
            
            // result: queried + created
            return Iterators.concat( queried, updated.iterator() );
        }
        catch( EntityFinderException e )
        {
            throw new QueryExecutionException( "Query '" + toString() + "' could not be executed", e );
        }
    }

    /**
     * @see Query#count()
     */
    public long count()
    {
        // find updated states
        UnitOfWorkInstance uow = ((ModuleUnitOfWork)unitOfWorkInstance).instance();
        EntityStoreUnitOfWork suow = uow.entityStoreUnitOfWork();
        final Map<String,EntityState> modifiedStates = suow != null && suow instanceof EntityStoreUnitOfWork2
                ? ((EntityStoreUnitOfWork2)suow).modified() : null;

        // no updates
        if (modifiedStates == null) {
            try {
                return entityFinder.countEntities( resultType.getName(), whereClause );
            }
            catch (EntityFinderException e) {
                e.printStackTrace();
                return 0;
            }
        }
        // with updates
        else {
            return Iterators.size( iterator() );
        }
    }

    @Override
    public String toString()
    {
        return "Find all " + resultType.getName() +
               ( whereClause != null ? " where " + whereClause.toString() : "" );
    }
}