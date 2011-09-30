/*
 * Copyright (c) 2009, Rickard Öberg. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.qi4j.runtime.composite;

import java.lang.reflect.Method;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import org.qi4j.spi.composite.CompositeInstance;

/**
 * JAVADOC
 */
public class ThisCompositeInvoker
    implements MethodInterceptor
{
    // FIXME fb71: instances are held in ThreadLocal as Enhancer Callback (see MixinModel:150);
    // and causing mem leak; I'm not quite sure how to fix this correctly; WeakReference is an
    // option but probably makes things slow; maybe the fix in MixinModel is enough
    private CompositeInstance compositeInstance;

    public ThisCompositeInvoker( CompositeInstance compositeInstance )
    {
        this.compositeInstance = compositeInstance;
    }

    public Object intercept( Object obj, Method method, Object[] args, MethodProxy proxy )
        throws Throwable
    {
        return compositeInstance.invokeProxy( method, args );
    }
}
