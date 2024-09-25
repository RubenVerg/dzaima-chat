#!/usr/bin/env python3
import importlib, subprocess, os

def git_lib(path):
  if os.path.exists(path): return path
  path2 = path+"Clone"
  print("using "+path2+" submodule; link custom path to "+path+" to override")
  subprocess.check_call(["git","submodule","update","--init",path2])
  return path2

uiPath = git_lib("UI")
b = importlib.import_module(uiPath+".build")

cp = b.build_ui_lib(uiPath)
cp += [
  b.maven_lib("org/jsoup", "jsoup", "1.14.3", "lib", "92af19ec57cc77637db4490f0f5011f0444d353209ce36083bac428f9b81a39c"),
  b.maven_lib("org/apache/httpcomponents/core5", "httpcore5", "5.3", "lib", "33b9eca7890ea3908a4d17fad19c70b10e30ce902ef85f913cdfdedf242aa14f"),
  b.maven_lib("org/apache/httpcomponents/client5", "httpclient5", "5.4", "lib", "40a72f6dca0f20d304b4bcb045a020522a6ada0b69aff48398988ecab5f6badc"),
]
b.jar("chat.jar", cp)
b.make_run("run", cp+["chat.jar"], "chat.ChatMain", "-ea")