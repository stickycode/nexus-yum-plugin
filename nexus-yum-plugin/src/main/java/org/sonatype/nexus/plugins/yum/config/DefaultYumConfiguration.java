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
package org.sonatype.nexus.plugins.yum.config;

import static javax.xml.bind.Marshaller.JAXB_FORMATTED_OUTPUT;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.configuration.application.NexusConfiguration;

import org.sonatype.nexus.plugins.yum.config.domain.XmlYumConfiguration;
import org.sonatype.nexus.plugins.yum.version.alias.AliasNotFoundException;
import org.sonatype.nexus.plugins.yum.version.alias.domain.AliasKey;
import org.sonatype.nexus.plugins.yum.version.alias.domain.AliasMapping;

@Component( role = YumConfiguration.class )
public class DefaultYumConfiguration
    implements YumConfiguration, Initializable
{
    public static final String YUM_XML = "yum.xml";

    private static final Logger log = LoggerFactory.getLogger( DefaultYumConfiguration.class );

    private static final Object LOAD_WRITE_MUTEX = new Object();

    private String filename = YUM_XML;

    private long fileLastModified = 0;

    @Requirement
    private NexusConfiguration nexusConfiguration;

    private File baseTempDir;

    private XmlYumConfiguration xmlYumConfiguration = new XmlYumConfiguration();

    private final ConcurrentHashMap<AliasKey, String> aliasMap = new ConcurrentHashMap<AliasKey, String>();

    private final Unmarshaller unmarshaller;

    private final Marshaller marshaller;

    private File configFile;

    public DefaultYumConfiguration()
        throws JAXBException
    {
        final JAXBContext jc = JAXBContext.newInstance( XmlYumConfiguration.class, AliasMapping.class );
        this.marshaller = jc.createMarshaller();
        this.marshaller.setProperty( JAXB_FORMATTED_OUTPUT, true );
        this.unmarshaller = jc.createUnmarshaller();
    }

    @Override
    public void load()
    {
        File configFile = getOrCreateConfigFile();
        synchronized ( LOAD_WRITE_MUTEX )
        {
            try
            {
                xmlYumConfiguration = (XmlYumConfiguration) unmarshaller.unmarshal( configFile );
                fileLastModified = getConfigFile().lastModified();
                fillAliasMap();
            }
            catch ( JAXBException e )
            {
                log.warn( "can't load config file staing with old config", e );
            }
        }
    }

    @Override
    public void saveConfig( XmlYumConfiguration configToUse )
    {
        synchronized ( LOAD_WRITE_MUTEX )
        {
            try
            {
                marshaller.marshal( configToUse, getConfigFile() );
                xmlYumConfiguration = configToUse;
                fileLastModified = getConfigFile().lastModified();
            }
            catch ( JAXBException e )
            {
                throw new RuntimeException( "can't save xmlyumConfig", e );
            }
        }
    }

    @Override
    public File getConfigFile()
    {
        if ( configFile == null || !configFile.getName().equals( filename ) )
        {
            configFile = new File( nexusConfiguration.getConfigurationDirectory(), filename );
        }
        return configFile;
    }

    @Override
    public String getVersion( String repositoryId, String alias )
        throws AliasNotFoundException
    {
        checkForUpdates();

        final AliasKey aliasKey = new AliasKey( repositoryId, alias );
        final String resultVersion = aliasMap.get( aliasKey );
        if ( resultVersion == null )
        {
            throw new AliasNotFoundException( "for " + aliasKey );
        }
        return resultVersion;
    }

    @Override
    public void setAlias( String repositoryId, String alias, String version )
    {
        final XmlYumConfiguration newConfig = new XmlYumConfiguration( xmlYumConfiguration );
        final AliasMapping newAliasMapping = new AliasMapping( repositoryId, alias, version );
        newConfig.getAliasMappings().add( newAliasMapping );
        saveConfig( newConfig );
        aliasMap.put( newAliasMapping.getAliasKey(), newAliasMapping.getVersion() );
    }

    @Inject
    public void setNexusConfiguration( NexusConfiguration nexusConfiguration )
    {
        this.nexusConfiguration = nexusConfiguration;
        // load();
    }

    @Override
    public void setRepositoryOfRepositoryVersionsActive( boolean active )
    {
        final XmlYumConfiguration newConfig = new XmlYumConfiguration( xmlYumConfiguration );
        newConfig.setRepositoryOfRepositoryVersionsActive( active );
        saveConfig( newConfig );
    }

    @Override
    public boolean isRepositoryOfRepositoryVersionsActive()
    {
        checkForUpdates();
        return xmlYumConfiguration.isRepositoryOfRepositoryVersionsActive();
    }

    @Override
    public XmlYumConfiguration getXmlYumConfiguration()
    {
        checkForUpdates();
        return xmlYumConfiguration;
    }

    private void checkForUpdates()
    {
        if ( getConfigFile().lastModified() > fileLastModified )
        {
            load();
        }
    }

    public String getFilename()
    {
        return filename;
    }

    @Override
    public void setFilename( String filename )
    {
        this.filename = filename;
    }

    private File getOrCreateConfigFile()
    {
        File configFile = getConfigFile();
        if ( !configFile.exists() )
        {
            saveConfig( xmlYumConfiguration );
        }
        return configFile;
    }

    private void fillAliasMap()
    {
        aliasMap.clear();
        if ( xmlYumConfiguration.getAliasMappings() != null )
        {
            for ( AliasMapping aliasMapping : xmlYumConfiguration.getAliasMappings() )
            {
                aliasMap.put( aliasMapping.getAliasKey(), aliasMapping.getVersion() );
            }
        }
    }

    @Override
    public boolean isDeleteProcessing()
    {
        checkForUpdates();
        return xmlYumConfiguration.isDeleteProcessing();
    }

    @Override
    public void setDeleteProcessing( boolean active )
    {
        final XmlYumConfiguration newConfig = new XmlYumConfiguration( xmlYumConfiguration );
        newConfig.setDeleteProcessing( active );
        saveConfig( newConfig );
    }

    @Override
    public long getDelayAfterDeletion()
    {
        checkForUpdates();
        return xmlYumConfiguration.getDelayAfterDeletion();
    }

    @Override
    public void setDelayAfterDeletion( long timeout )
    {
        final XmlYumConfiguration newConfig = new XmlYumConfiguration( xmlYumConfiguration );
        newConfig.setDelayAfterDeletion( timeout );
        saveConfig( newConfig );
    }

    @Override
    public File getBaseTempDir()
    {
        if ( baseTempDir == null )
        {
            baseTempDir = new File( nexusConfiguration.getTemporaryDirectory(), "yum" );
        }

        return baseTempDir;
    }

    @Override
    public int getMaxParallelThreadCount()
    {
        checkForUpdates();
        return xmlYumConfiguration.getMaxParallelThreadCount();
    }

    @Override
    public boolean isActive()
    {
        checkForUpdates();
        return xmlYumConfiguration.isActive();
    }

    @Override
    public void setActive( boolean active )
    {
        final XmlYumConfiguration newConfig = new XmlYumConfiguration( xmlYumConfiguration );
        newConfig.setActive( active );
        saveConfig( newConfig );
    }

    @Override
    public void initialize()
        throws InitializationException
    {
        load();
    }
}
