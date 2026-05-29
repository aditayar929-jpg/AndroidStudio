@Suppress("JavaPluginLanguageLevel")
plugins {
  id("java-library")
  id("org.jetbrains.kotlin.jvm")
  alias(libs.plugins.protobuf)
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(17))
  }
}

kotlin {
  jvmToolchain(17)
}

val reapiProtoRoot = layout.projectDirectory.dir("../reapi")

sourceSets {
  main {
    proto {
      srcDir(reapiProtoRoot)
      include("build/bazel/**/*.proto")
    }
  }
}

dependencies {
  api(libs.google.protobuf.java)
  api(libs.google.protobuf.kotlin)
  api(libs.grpc.protobuf)
  api(libs.grpc.stub)
  api(libs.grpc.kotlin.stub)
  compileOnly(libs.kotlinx.coroutines.core)
  implementation(libs.grpc.netty.shaded)

  compileOnly("javax.annotation:javax.annotation-api:1.3.2")
}

protobuf {
  protoc {
    artifact = "com.google.protobuf:protoc:${libs.versions.protobufVersion.get()}"
  }
  plugins {
    create("grpc") {
      artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.ioGrpcVersion.get()}"
    }
    create("grpckt") {
      artifact = "io.grpc:protoc-gen-grpc-kotlin:${libs.versions.ioGrpcStubVersion.get()}:jdk8@jar"
    }
  }

  generateProtoTasks {
    all().forEach { task ->
      task.plugins {
        create("grpc")
        create("grpckt")
      }
      task.builtins {
        create("kotlin")
      }
    }
  }
}
