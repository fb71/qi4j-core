/* 
 * polymap.org
 * Copyright 2011, Polymap GmbH. All rights reserved.
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
package org.qi4j.runtime.unitofwork;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * Test implementation <b>without concurrency control</b>.
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public class FakeConcurrentMap<K, V>
        extends HashMap<K, V>
        implements ConcurrentMap<K, V> {

    
    public FakeConcurrentMap() {
        super();
    }

    public FakeConcurrentMap( int initialCapacity, float loadFactor ) {
        super( initialCapacity, loadFactor );
    }

    public FakeConcurrentMap( int initialCapacity ) {
        super( initialCapacity );
    }

    public FakeConcurrentMap( Map<? extends K, ? extends V> m ) {
        super( m );
    }

    public V putIfAbsent( K key, V value ) {
        return super.put( key, value );
    }

    public boolean remove( Object key, Object value ) {
        return super.remove( key ) != null;
    }

    public V replace( K key, V value ) {
        throw new UnsupportedOperationException( "replace()" );
    }

    public boolean replace( K key, V oldValue, V newValue ) {
        throw new UnsupportedOperationException( "replace()" );
    }

}
