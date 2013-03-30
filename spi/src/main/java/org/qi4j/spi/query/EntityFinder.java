/*
 * Copyright 2008 Alin Dreghiciu.
 * Copyright 2009 Niclas Hedhman.
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
package org.qi4j.spi.query;

import org.qi4j.api.common.Optional;
import org.qi4j.api.entity.EntityComposite;
import org.qi4j.api.entity.EntityReference;
import org.qi4j.api.query.grammar.BooleanExpression;
import org.qi4j.api.query.grammar.OrderBy;

import com.google.common.base.Predicate;

/**
 * JAVADOC Add JavaDoc
 */
public interface EntityFinder
{
    /**
     * Allows {@link EntityFinder} implementations to post-process results
     * *after* an {@link EntityComposite} was created for the reference
     * 
     * @see EntityQuery 
     * @author fb71
     */
    public abstract class PostProcessEntityReference
            extends EntityReference
            implements Predicate<EntityComposite> {

        public PostProcessEntityReference( String identity ) {
            super( identity );
        }
    }
    
    
    Iterable<EntityReference> findEntities( String resultType,
                                            @Optional BooleanExpression whereClause,
                                            @Optional OrderBy[] orderBySegments,
                                            @Optional Integer firstResult,
                                            @Optional Integer maxResults
    )
        throws EntityFinderException;

    EntityReference findEntity( String resultType, @Optional BooleanExpression whereClause )
        throws EntityFinderException;

    long countEntities( String resultType, @Optional BooleanExpression whereClause )
        throws EntityFinderException;
}