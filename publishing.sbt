publishTo in ThisBuild <<= (version) { version: String =>
  val bpsMavenRepository = Resolver.sftp(name= "bpsMavenRepo", hostname = Some("bps-artifacts.turner.com"), port = Some(22),
    basePath = Some("/data/maven2"))
  if (version.trim.endsWith("SNAPSHOT"))
    Some(Opts.resolver.sonatypeSnapshots)
  else
    Some(bpsMavenRepository)
}