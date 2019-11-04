package co.sodalabs.updaterengine.feature.lrucache

import java.io.IOException

interface IDiskLruCache {
    /**
     * Opens the cache in {@code directory}, creating a cache if none exists
     * there.
     *
     * @throws IOException if reading or writing the cache directory fails
     */
    @Throws(IOException::class)
    fun open()

    /**
     * Closes this cache. Stored values will remain on the filesystem.
     */
    @Throws(IOException::class)
    fun close()

    /**
     * Force buffered operations to the filesystem.
     */
    @Throws(IOException::class)
    fun flush()

    /**
     * Returns true if this cache has been opened.
     */
    fun isOpened(): Boolean

    /**
     * Returns true if this cache has been closed.
     */
    fun isClosed(): Boolean

    /**
     * Returns the number of bytes currently being used to store the values in
     * this cache. This may be greater than the max size if a background
     * deletion is pending.
     */
    fun size(): Long

    /**
     * Returns an editor for the entry named {@code key}, or null if another
     * edit is in progress.
     */
    @Throws(IOException::class)
    fun edit(key: String): DiskLruCache.Editor

    /**
     * Drops the entry for {@code key} if it exists and can be removed. Entries
     * actively being edited cannot be removed.
     *
     * @return true if an entry was removed.
     */
    @Throws(IOException::class)
    fun remove(key: String): Boolean
}