/**
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2007-2012 Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.plugins.yum.repository.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.sonatype.nexus.plugins.yum.repository.YumRepository;

public class YumRepositoryCache
{
    private final Map<String, YumRepository> cache = new ConcurrentHashMap<String, YumRepository>();

    public YumRepository lookup( String id, String version )
    {
        YumRepository yumRepository = cache.get( hash( id, version ) );

        if ( ( yumRepository != null ) && !yumRepository.getBaseDir().exists() )
        {
            yumRepository.setDirty();
        }

        return yumRepository;
    }

    public void cache( YumRepository yumRepository )
    {
        cache.put( hash( yumRepository.getId(), yumRepository.getVersion() ), yumRepository );
    }

    public void markDirty( String id, String version )
    {
        YumRepository repository = cache.get( hash( id, version ) );
        if ( repository != null )
        {
            repository.setDirty();
        }
    }

    private String hash( String id, String version )
    {
        return id + "/" + version;
    }

}
