# Nexus Yum Plugin

A plugin for Sonatype Nexus 1.9.2.x or 2.X which recognizes RPMs in Nexus Maven repositories and generates 
Yum metadata, so that RedHat-compatible system can use Nexus as software repository. 

## Content

1. [Audience](#audience)
1. [History](#history)
1. [Features](#features)
1. [Help & Issues](#help--issues)
1. [Installation](#installation)
1. [Configuration](#configuration)
1. [Getting Started](#getting-started)
1. [How to Build](#how-to-build)


## Audience

The Nexus Yum Plugin is for all guys, who are deploying Java application for RedHat-compatible (RHEL, Centos, Fedora) servers and deploy via RPM.

### Default use case

![CLD at IS24][1]
See [Deploy Java Web Application via RPM](#deploy-java-web-application-via-rpm).

## History

[Sonatype Nexus][2] is a common repository manager for [Maven][3], used by many companies to manage 
their Java artifacts like JAR, WAR, pom.xml files. At [ImmobilienScout24][4] the DevOps-guys started 
[to deploy their configurations][5] and 
[java applications via RPMs][6] and 
wanted to have a repository manager for their application RPMs. Why don't extend Sonatype Nexus to host RPMs as well?

## Features

- Use a Maven repository, hosted in Nexus, containing RPMs as if it is a Yum repository. This leverages the virtual repository mechanism in Nexus which allows you to use Maven tooling to deploy RPMs into a Maven repository but still allow Yum clients to interact with the repository using the protocol it understands.
- Yum repositories are automatically updated if you upload/deploy/delete a new RPM into Nexus.
- Full group support so that you can logically group a set of Yum repositories behind a single URL.
- Have versioned views on repositories: <pre>http://your.nexus/nexus/service/local/yum/repos/releases/1.2.3/</pre> gives you a Yum repository with all packages in version *1.2.3* in repository releases.
- You can define aliases for specific versions eg. *production=1.2* and *testing=2.0* and access them via the alias: <pre>http://your.nexus/nexus/service/local/yum/repos/releases/testing/</pre> and <pre>http://your.nexus/nexus/service/local/yum/repos/releases/production/</pre> to get constant repository URLs for your servers. A new release is then applied to the server via setting the alias to a new version.
- Create Yum createrepo tasks manually via web interface.
- Multiple createrepo tasks on the same repository get merged.
- Use Yum group repositories as target of staging repositories (Nexus Pro)

## Help & Issues

Ask for help at our [Google Group][7] or [create a new issue][8].

## Installation

1. [Install Sonatype Nexus][9]
1. Download latest *nexus-yum-plugin-bundle.zip* from our downloads page
1. Unzip the bundle to *$NEXUS_WORK_DIR/plugin-repository/*. The default for *$NEXUS_WORK_DIR* is *~/sonatype-work/nexus/*. For example:
    unzip nexus-yum-plugin-1.13-bundle-zip -d $NEXUS_WORK_DIR/plugin-repository/
1. Install [createrepo][10] using your update manager (*yum*, *apt-get*, etc.) eg.
    sudo yum install createrepo
1. Make sure that in *Nexus Adminstration --> Settings --> Application Server Settings (optional) --> Base URL* is set to a vaild URL like :
    http://your.nexus.domain:8081/nexus
1. Sometimes *Force Base URL* is nessessary, too, see [ISSUE 4][11] . Otherwise the plugin can't determine the server URL and you got RPM locations like *null/content/repositories/*... in *primary.xml*.
1. Configure Nexus Yum Plugin via *yum.xml*. See Configuration.
1. Restart Nexus. Eg.
    sudo service nexus stop
    sudo service nexus start

Now the plugin should be installed.

## Configuration

Here, you'll find everything about configuring Nexus Yum Plugin.

### Location

The configuration of the Nexus Yum Plugin can be found in *yum.xml* in the same directory as *nexus.xml* :
	$NEXUS_WORK_DIR/conf/yum.xml
Default:
	~/sonatype-work/nexus/conf/yum.xml

### Example

Example *yum.xml*:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <!-- timeout for requests for a filtered (versionized) repository -->
  <repositoryCreationTimeout>150</repositoryCreationTimeout><!-- in seconds -->
  
  <!-- enables or disables the creation of a repository of repositories -->
  <repositoryOfRepositoryVersionsActive>true</repositoryOfRepositoryVersionsActive>
  
  <!-- enables or disables of delete rpm events via nexus -->
  <deleteProcessing>true</deleteProcessing>
  
  <!-- delay after which the rebuild of a repository is triggered in which one or more rpms got deleted -->
  <delayAfterDeletion>10</delayAfterDeletion><!-- in seconds -->
  
  <!-- configured aliases -->
  <aliasMappings>
    <aliasMapping>
      <repoId>releases</repoId>
      <alias>trunk</alias>
      <version>5.1.15-2</version>
    </aliasMapping>
    <aliasMapping>
      <repoId>releases</repoId>
      <alias>production</alias>
      <version>5.1.15-1</version>
    </aliasMapping>
  </aliasMappings>
</configuration>
```

## Getting Started

Here we provide some typical scenarios in which the _nexus-yum-plugin_ is used.

### Deploy Java Web Application via RPM

#### Prepare the _pom.xml_

Assume you have a standard Java web application build with [Maven][3]. To build a RPM of your WAR file you could
use the [rpm-maven-plugin][12] by Codehaus. Its goal _attached-rpm_ automatically attaches the RPM file as Maven 
build artifact so that the RPM is uploaded to Nexus in the _deploy_ phase. A minimal _pom.xml_ would look like this:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.sonatype.nexus.yum.its</groupId>
  <artifactId>war-rpm-test</artifactId>
  <version>1.0</version>
  <packaging>war</packaging>
  <build>
    <plugins>
      <!-- use rpm-maven-plugin to package the war into an rpm -->
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>rpm-maven-plugin</artifactId>
        <version>2.1-alpha-2</version>
        <executions>
          <execution>
            <id>build-rpm</id>
            <goals>
           	  <!-- this goal automatically adds the rpm as Maven build artifact -->
              <goal>attached-rpm</goal>
            </goals>
          </execution>
        </executions>
        <configuration>
          <group>Applications/Internet</group>
          <copyright>EPL</copyright>
          <!-- require tomcat6 as webapp container -->
          <requires>
            <require>tomcat6</require>
          </requires>
          <mappings>
            <mapping>
              <!-- put webapp files to standard tomcat6 webapps directory -->
              <directory>/var/lib/tomcat6/webapps/${project.build.finalName}</directory>
              <sources>
                <source>
                  <location>${project.build.directory}/${project.build.finalName}</location>
                </source>
              </sources>
            </mapping>
          </mappings>
        </configuration>
      </plugin>
    </plugins>
  </build>
  <!-- deploy build artifacts (pom,war,rpm) to Nexus --> 
  <distributionManagement>
    <repository>
      <id>releases</id>
      <name>Releases Repository</name>
      <url>http://your.nexus.domain/nexus/content/repositories/releases</url>
    </repository>
  </distributionManagement>
</project>
```
	
#### Deploy RPM to Nexus
	
If you have the _nexus-yum-plugin_ [installed](#installation) and deploy your application via

	mvn deploy
	
to Nexus, the RPM is uploaded and on Nexus side the _yum_ metatdata is generated asynchronously. You can 
browse the _yum_ metadata here:

	http://your.nexus.domain/nexus/content/repositories/releases/repodata

#### Install RPM on Server

Your RPM was built and is stored in a Nexus _yum_ repository (if _nexus_yum_plugin_ is installed, each Maven 
repository gets a _yum_ repository after uploading an RPM). The next step is to install the RPM on your RHEL-compatible server.

First of all, we need to introduce our new _yum_ repository to the server. Therefore, we create a new _yum repository file_ called
_nexus-releases.repo_. The default location for such _yum repository file_ is _/etc/yum.repos.d_, but may differ depending on 
your distribution and configuration.:

	sudo vi /etc/yum.repos.d/nexus-releases.repo
	
Insert the following content:

	[nexus-releases]
	name=Nexus Releases Repository
	baseurl=http://your.nexus.domain/nexus/content/repositories/releases
	enabled=1
	protect=0
	gpgcheck=0
	metadata_expire=30s
	autorefresh=1
	type=rpm-md 
	
and save the file. Now, the server will ask Nexus for new software packages and updates. After that just install your web application 
and all its dependencies (Tomcat, Java, etc.) via:

	sudo yum install war-rpm-test
	
and start tomcat:

	sudo service tomcat start
	
That's it.

#### Update RPM

To update the web application on your server just call:

	sudo service tomcat stop
	sudo yum update
	sudo service tomcat start
	
The tomcat restart is optional depending on your webapp and configuration, but always a good choice.

#### Summary

The _nexus-yum-plugin_ makes deploying Java application to _real_ RHEL-compatible servers really easy and works as a 
relyable platform for your deployments.  

## How to build

The build process is based on [Apache Maven 3][3]. You must have [createrepo][10] installed in order to execute all 
the integration tests. Just do a

    mvn package 

to run all tests and create a plugin bundle.

[1]: https://raw.github.com/sonatype/nexus-yum-plugin/master/docs/images/NeuxsYumPlugin.png
[2]: http://nexus.sonatype.org
[3]: http://maven.apache.org
[4]: http://www.immobilienscout24.de
[5]: http://blog.schlomo.schapiro.org/2011/05/configuration-management-with.html
[6]: http://www.slideshare.net/actionjackx/automated-java-deployments-with-rpm
[7]: https://groups.google.com/group/nexus-yum-plugin/
[8]: https://github.com/sonatype/nexus-yum-plugin/issues/new
[9]: http://www.sonatype.com/books/nexus-book/reference/install-sect-install.html
[10]: http://createrepo.baseurl.org/
[11]: http://code.google.com/p/nexus-yum-plugin/issues/detail?id=4
[12]: http://mojo.codehaus.org/rpm-maven-plugin/
