/* 
 * polymap.org
 * Copyright 2013, Falko Bräutigam. All rights reserved.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 */
package org.qi4j.spi.entitystore;

import java.util.Map;

import org.qi4j.spi.entity.EntityState;


/**
 * 
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public interface EntityStoreUnitOfWork2
        extends EntityStoreUnitOfWork {
    
    /**
     * 
     *
     * @return ALl modified states, or an empty {@link Iterable}.
     */
    public Map<String,EntityState> modified();

}
