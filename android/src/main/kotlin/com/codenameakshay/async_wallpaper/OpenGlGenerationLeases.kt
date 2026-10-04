package com.codenameakshay.async_wallpaper

import java.io.File

/** Tracks staged and live renderer references so cleanup cannot invalidate context-loss recovery. */
internal object OpenGlGenerationLeases {
  private val lock = Any()
  private val staged = mutableSetOf<File>()
  private val leases = mutableMapOf<File, Int>()

  fun registerStaged(directory: File) = synchronized(lock) {
    staged += directory.canonicalFile
  }

  fun abandonStaged(directory: File) = synchronized(lock) {
    staged -= directory.canonicalFile
  }

  fun acquire(directory: File) = synchronized(lock) {
    val key = directory.canonicalFile
    leases[key] = (leases[key] ?: 0) + 1
  }

  fun release(directory: File) = synchronized(lock) {
    val key = directory.canonicalFile
    val count = leases[key] ?: return@synchronized
    if (count <= 1) {
      leases.remove(key)
    } else {
      leases[key] = count - 1
    }
  }

  fun protectedDirectories(): Set<File> = synchronized(lock) {
    staged + leases.keys
  }
}
