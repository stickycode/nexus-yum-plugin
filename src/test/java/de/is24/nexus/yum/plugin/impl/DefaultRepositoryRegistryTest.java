package de.is24.nexus.yum.plugin.impl;

import static de.is24.test.hamcrest.FileMatchers.exists;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import java.io.File;
import java.util.ArrayList;
import javax.inject.Inject;
import javax.inject.Named;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sonatype.nexus.proxy.maven.MavenRepository;
import org.sonatype.plexus.appevents.Event;
import org.sonatype.plexus.appevents.EventListener;
import de.is24.nexus.yum.AbstractRepositoryTester;
import de.is24.nexus.yum.guice.NexusTestRunner;
import de.is24.nexus.yum.plugin.RepositoryRegistry;
import de.is24.nexus.yum.service.RepositoryRpmManager;


@RunWith(NexusTestRunner.class)
public class DefaultRepositoryRegistryTest extends AbstractRepositoryTester {
  private static final String REPO_ID = "rpm-snapshots";
  private static final String REPOSITORY_RPM_FILENAME = "is24-rel-" + REPO_ID + "-1.3-repo-1-1.noarch.rpm";

  @Inject
  @Named(RepositoryRegistry.DEFAULT_BEAN_NAME)
  private RepositoryRegistry repositoryRegistry;

  @Inject
  @Named(RepositoryRpmManager.DEFAULT_BEAN_NAME)
  private RepositoryRpmManager repositoryRpmManager;

  @Test
  public void shouldScanRepository() throws Exception {
    MavenRepository repository = createMock(MavenRepository.class);
    expect(repository.getId()).andReturn(REPO_ID).anyTimes();
    expect(repository.getLocalUrl()).andReturn(new File(".", "target/test-classes/repo").toURI().toString()).anyTimes();
    replay(repository);

    repositoryRegistry.registerRepository(repository);
    Thread.sleep(5000);
    assertNotNull(repositoryRegistry.findRepositoryForId(REPO_ID));
    assertThat(repositoryRpmManager.getYumRepository().getFile(REPOSITORY_RPM_FILENAME), exists());
  }

  @Test
  public void shouldUnregisterRepository() throws Exception {
    MavenRepository repository = createRepository(true);
    repositoryRegistry.registerRepository(repository);
    assertTrue(repositoryRegistry.isRegistered(repository));
    repositoryRegistry.unregisterRepository(repository);
    assertFalse(repositoryRegistry.isRegistered(repository));
  }

  @SuppressWarnings("serial")
  public static class QueueingEventListener extends ArrayList<Event<?>> implements EventListener {
    public void onEvent(Event<?> evt) {
      add(evt);
    }

  }
}