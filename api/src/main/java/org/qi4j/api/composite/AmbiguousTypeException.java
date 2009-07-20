/*
 * Copyright 2008 Niclas Hedhman. All rights Reserved.
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
package org.qi4j.api.composite;

import java.util.Arrays;
import org.qi4j.api.mixin.MixinMappingException;

/**
 * This Exception is thrown when more than one Composite implements a MixinType
 * that one tries to use to create a Composite instance from.
 * <p>
 * For instance;
 * </p>
 * <code><pre>
 * public interface AbcComposite extends TransientComposite, Abc
 * {}
 *
 * public interface DefComposite extends TransientComposite, Def
 * {}
 *
 * public interface Abc
 * {}
 *
 * public interface Def extends Abc
 * {}
 *
 *
 * TransientBuilder cb = factory.newTransientBuilder( Abc.class );
 * </pre></code>
 * <p>
 * In the code above, both the AbcComposite and DefComposite implement Abc, and therefore
 * the <code>newTransientBuilder</code> method can not unambiguously figure out which
 * one is intended.
 * </p>
 */
public class AmbiguousTypeException extends MixinMappingException
{
    private static final long serialVersionUID = 1L;

    private final Class<?> mixinType;
    private Iterable<Class<?>> matchingTypes;

    public AmbiguousTypeException( Class<?> mixinType, Class<?>... matchingTypes )
    {
        this( mixinType, Arrays.asList( matchingTypes ) );
    }

    public AmbiguousTypeException( Class<?> mixinType, Iterable<Class<?>> matchingTypes )
    {
        super( "More than one visible CompositeType implements mixintype " + mixinType.getName() + ":" + matchingTypes );
        this.mixinType = mixinType;
        this.matchingTypes = matchingTypes;
    }

    public Class<?> mixinType()
    {
        return mixinType;
    }

    public Iterable<Class<?>> matchingTypes()
    {
        return matchingTypes;
    }
}
