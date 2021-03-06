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
package org.sonatype.nexus.client.internal.rest.jersey.subsystem;

import static com.sun.jersey.multipart.Boundary.addBoundary;
import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM_TYPE;
import static javax.ws.rs.core.MediaType.MULTIPART_FORM_DATA_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_HTML;
import static org.apache.commons.lang.StringUtils.defaultIfEmpty;

import java.io.IOException;

import org.codehaus.jackson.map.ObjectMapper;
import org.sonatype.nexus.client.core.spi.SubsystemSupport;
import org.sonatype.nexus.client.core.subsystem.artifact.ArtifactMaven;
import org.sonatype.nexus.client.core.subsystem.artifact.MavenArtifact;
import org.sonatype.nexus.client.core.subsystem.artifact.ResolveRequest;
import org.sonatype.nexus.client.core.subsystem.artifact.ResolveResponse;
import org.sonatype.nexus.client.core.subsystem.artifact.UploadRequest;
import org.sonatype.nexus.client.core.subsystem.repository.Repositories;
import org.sonatype.nexus.client.core.subsystem.repository.Repository;
import org.sonatype.nexus.client.core.subsystem.repository.maven.MavenHostedRepository;
import org.sonatype.nexus.client.rest.jersey.JerseyNexusClient;
import org.sonatype.nexus.rest.model.ArtifactCoordinate;
import org.sonatype.nexus.rest.model.RepositoryResource;

import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.multipart.FormDataMultiPart;
import com.sun.jersey.multipart.file.FileDataBodyPart;

public class JerseyMavenArtifact
    extends SubsystemSupport<JerseyNexusClient>
    implements MavenArtifact
{

    private final ArtifactMaven artifactMaven;

    private final Repositories repositories;

    public JerseyMavenArtifact( final JerseyNexusClient nexusClient, final ArtifactMaven artifactMaven,
                                final Repositories repositories )
    {
        super( nexusClient );
        this.artifactMaven = artifactMaven;
        this.repositories = repositories;
    }

    @Override
    public ArtifactCoordinate upload( UploadRequest req )
    {
        final FormDataMultiPart entity = createEntity( req );
        final ClientResponse response =
            getNexusClient().serviceResource( "artifact/maven/content" ).type( addBoundary( MULTIPART_FORM_DATA_TYPE ) ).accept(
                TEXT_HTML ).post( ClientResponse.class, entity );

        final String content = response.getEntity( String.class );
        response.close();
        if ( response.getStatus() < 300 )
        {
            return parseEntity( content );
        }

        throw new ClientHandlerException( "Upload failed due to status code " + response.getStatus() + ".\nResponse: "
            + content );
    }

    @Override
    public void delete( ResolveRequest req )
    {

        final Repository<MavenHostedRepository, RepositoryResource> repository =
            repositories.get( req.getRepositoryId() );
        final ResolveResponse resolvedArtifact = artifactMaven.resolve( req );
        if ( resolvedArtifact == null )
        {
            throw new IllegalArgumentException( "Could not find artifact for given request." );
        }
        final String urlToDelete = repository.settings().getContentResourceURI() + resolvedArtifact.getRepositoryPath();
        getNexusClient().getClient().resource( urlToDelete ).accept( getNexusClient().getMediaType() ).delete();
    }

    private ArtifactCoordinate parseEntity( final String content )
    {
        final ObjectMapper mapper = new ObjectMapper();
        try
        {
            return mapper.readValue( content, ArtifactCoordinate.class );
        }
        catch ( IOException e )
        {
            throw new ClientHandlerException( "Could not parse response.", e );
        }
    }

    private FormDataMultiPart createEntity( UploadRequest req )
    {
        @SuppressWarnings( "resource" )
        final FormDataMultiPart entity = new FormDataMultiPart().field( "r", req.getRepositoryId() );
        if ( req.isHasPom() )
        {
            entity.field( "hasPom", "true" ).field( "c", defaultIfEmpty( req.getClassifier(), "" ) ).field( "e",
                req.getExtension() ).bodyPart(
                new FileDataBodyPart( "file", req.getPomFile(), APPLICATION_OCTET_STREAM_TYPE ) );
        }
        else
        {
            entity.field( "g", req.getGroupId() ).field( "a", req.getArtifactId() ).field( "v", req.getVersion() ).field(
                "p", req.getPackaging() ).field( "c", defaultIfEmpty( req.getClassifier(), "" ) ).field( "e",
                req.getExtension() );
        }
        entity.bodyPart( new FileDataBodyPart( "file", req.getFile(), APPLICATION_OCTET_STREAM_TYPE ) );
        return entity;
    }
}
