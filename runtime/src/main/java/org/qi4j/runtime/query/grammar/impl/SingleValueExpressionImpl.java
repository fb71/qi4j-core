/*
 * Copyright 2008 Alin Dreghiciu.
 * Copyright 2008 Niclas Hedhman.
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
package org.qi4j.runtime.query.grammar.impl;

import org.qi4j.api.query.grammar.SingleValueExpression;

/**
 * A simple value holder.
 */
public final class SingleValueExpressionImpl<T>
    implements SingleValueExpression<T>
{

    private final T value;

    /**
     * Constructor.
     *
     * @param value value
     */
    public SingleValueExpressionImpl( final T value )
    {
        this.value = value;
    }

    /**
     * Getter.
     *
     * @return value
     */
    public T value()
    {
        return value;
    }

    @Override
    public String toString()
    {
        return value.toString();
    }
}