/*
 * Copyright (c) 2008, Rickard Öberg. All Rights Reserved.
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

/**
 * Method instance pool that keeps a linked list. Uses synchronization
 * to ensure that instances are acquired and returned in a thread-safe
 * manner.
 */
public final class UnsynchronizedCompositeMethodInstancePool
    implements CompositeMethodInstancePool
{
    private CompositeMethodInstance first = null;

    public CompositeMethodInstance getInstance()
    {
        CompositeMethodInstance instance = first;
        if( instance != null )
        {
            first = instance.getNext();
        }
        return instance;
    }

    public void returnInstance( CompositeMethodInstance instance )
    {
        instance.setNext( first );
        first = instance;
    }
}