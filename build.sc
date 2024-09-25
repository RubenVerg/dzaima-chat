import mill._, javalib._

val (os, arch) = {
  val _os = System.getProperty("os.name")
  val _arch = System.getProperty("os.arch")
  val os =
    if (_os.startsWith("Windows")) "windows"
    else if (_os.startsWith("Mac")) "macos"
    else if (_os.startsWith("Linux") || _os.startsWith("LINUX")) "linux"
    else {
      System.err.println(s"Unknown OS ${_os}, assuming Linux")
      "linux"
    }
  val arch =
    if (_arch == "amd64") "x64"
    else if (_arch == "aarch64") "arm64"
    else {
      System.err.println(s"Unkown architecture ${_arch}")
      System.exit(1)
    }
  (os, arch)
}

val skijaPlatform = s"$os-$arch"
val lwjglPlatform = if (arch == "x64") os else s"$os-$arch"

val lwjglVersion = "3.3.0"

object chat extends RootModule with JavaModule {
  def ivyDeps = Agg(ivy"org.jsoup:jsoup:1.14.3")
  def moduleDeps = Seq(UIClone)

  // override def sources = T.sources(super.sources() ++ { val src = build.millSourcePath / "src" ; println(src) ; Seq(PathRef(src)) })
  // override def resources = T.sources(build.millSourcePath / "res")

  object UIClone extends JavaModule {
    def ivyDeps = Agg(
      ivy"io.github.humbleui:skija-$skijaPlatform:0.116.1",
      ivy"io.github.humbleui:jwm:0.4.13",
      ivy"org.lwjgl:lwjgl:$lwjglVersion",
      ivy"org.lwjgl:lwjgl:$lwjglVersion;classifier=natives-$lwjglPlatform",
      ivy"org.lwjgl:lwjgl-nfd:$lwjglVersion",
      ivy"org.lwjgl:lwjgl-nfd:$lwjglVersion;classifier=natives-$lwjglPlatform",
      ivy"org.lwjgl:lwjgl-glfw:$lwjglVersion",
      ivy"org.lwjgl:lwjgl-glfw:$lwjglVersion;classifier=natives-$lwjglPlatform",
      ivy"org.lwjgl:lwjgl-opengl:$lwjglVersion",
      ivy"org.lwjgl:lwjgl-opengl:$lwjglVersion;classifier=natives-$lwjglPlatform",
    )

    // override def sources = T.sources(build.millSourcePath / "UIClone" / "src")
  }
}
