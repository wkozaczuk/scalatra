## Scalatra [![Build Status](http://jenkins.backchat.io/buildStatus/icon?job=scalatra_2.3.x)](http://jenkins.backchat.io/job/scalatra_2.3.x/)

Scalatra is a tiny, [Sinatra](http://www.sinatrarb.com/)-like web framework for
[Scala](http://www.scala-lang.org/).

This is a fork of the 2.3.1 release meant to fix number of bugs in atmosphere integration logic more specifally in atmosphere/src tree of the project.

**Here are following changes made to address bugs in handling websocket and SSE connects and disconnects** : 
* Fixed [#429](https://github.com/scalatra/scalatra/issues/429) defect by reworkng ScalatraAtmosphereHandler NOT to use HttpSession to store AtmosphereClient; instead AtmosphereResourceSession is used for this purpose - [patch1](https://github.com/wkozaczuk/scalatra/commit/0cbe9990e2a32f4c4190d57f8ddc090a0e8f1fb1) [patch2](https://github.com/wkozaczuk/scalatra/commit/9d057a2169729d5865b73268d09ba7202795096f)
* Fixed [#555](https://github.com/scalatra/scalatra/issues/555) defect by changing AtmosphereClient.broadcast() methods to not use logic to convert Java futures into Scala futures - [patch](https://github.com/wkozaczuk/scalatra/commit/d46572a68efbc2a3de756a2fc6795ef46e0602c8)

## Example

```scala
import org.scalatra._

class ScalatraExample extends ScalatraServlet {
  get("/") {
    <h1>Hello, world!</h1>
  }
}
```

## Documentation

If you're just starting out, see the [installation](http://www.scalatra.org/2.2/getting-started/installation.html) and [first project](http://www.scalatra.org/2.2/getting-started/first-project.html) sections of our website. 

Once you've done that, take a look at the [Scalatra Guides](http://www.scalatra.org/guides/) for documentation on all aspects of the framework, code examples, and more. We also have an extensive selection of [Example Applications](https://github.com/scalatra/scalatra-website-examples) which accompany the tutorials in the Scalatra Guides.

## Latest version

The latest version of Scalatra is `2.2.2`, and is published to [Maven Central](http://repo1.maven.org/maven2/org/scalatra).

```scala
libraryDependencies += "org.scalatra" %% "scalatra" % "2.2.2"
```

### Development version

The develop branch is published as `2.3.0-SNAPSHOT` to [OSSRH](http://oss.sonatype.org/content/repositories/snapshots/org/scalatra). 

```scala
resolvers += "Sonatype Nexus Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

libraryDependencies += "org.scalatra" %% "scalatra" % "2.3.0-SNAPSHOT"
```

## Community

* Mailing list: [scalatra-user](http://groups.google.com/group/scalatra-user)
* IRC: #scalatra on irc.freenode.org
