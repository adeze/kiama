resolvers += Resolver.url ("scalasbt",
    new URL ("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases")
) (Resolver.ivyStylePatterns)
 
addSbtPlugin("com.jsuereth" % "xsbt-gpg-plugin" % "0.6")

// addSbtPlugin ("de.johoop" % "findbugs4sbt" % "1.1.2")
