import mill._, javalib._

val (system, arch) = {
  val _os = System.getProperty("os.name")
  val _arch = System.getProperty("os.arch")
  val system =
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
  (system, arch)
}

val skijaPlatform = s"$system-$arch"
val lwjglPlatform = if (arch == "x64") system else s"$system-$arch"

val lwjglVersion = "3.3.0"

val uiDir = "UIClone"

object chat extends RootModule with JavaModule {
  def ivyDeps = Agg(ivy"org.jsoup:jsoup:1.14.3")
  def moduleDeps = Seq(ui)
  
  def setup() = T.command {
    os.proc("git", "submodule", "init").call(stdout = os.Inherit)
    os.proc("git", "submodule", "update").call(stdout = os.Inherit)
    try
      os.copy(T.workspace / uiDir / "res" / "base", T.workspace / "res" / "base")
    catch { case ex: java.nio.file.FileAlreadyExistsException => () }
    if (!os.exists(T.workspace / "accounts" / "profile.json")) System.err.println("Don't forget to create accounts/profile.json!")
  }

  object ui extends JavaModule {
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

    override def sources = T.sources(T.workspace / uiDir / "src")
  }
}
