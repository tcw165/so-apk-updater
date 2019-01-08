package co.sodalabs.updaterengine.data

import java.io.File

/**
 * File guaranteed to have a santitized name (though not a sanitized path to the parent dir).
 * Useful so that we can use Java's type system to enforce that the file we are accessing
 * doesn't contain illegal characters.
 * Sanitized names are those which only have the following characters: [A-Za-z0-9.-_]
 */
@SuppressWarnings("serial")
class SanitizedFile : File {

    companion object {

        /**
         * Removes anything that is not an alpha numeric character, or one of "-", ".", or "_".
         */
        fun sanitizeFileName(name: String): String {
            return name.replace("[^A-Za-z0-9-._ ]".toRegex(), "")
        }

        /**
         * This is dangerous, but there will be some cases when all we have is an absolute file
         * path that wasn't given to us from user input. One example would be asking Android for
         * the path to an installed .apk on disk. In such situations, we can't meaningfully
         * sanitize it, but will still need to pass to a function which only allows SanitizedFile's
         * as arguments (because they interact install, e.g. shells).
         *
         *
         * To illustrate, imagine perfectly valid file path: "/tmp/../secret/file.txt",
         * one cannot distinguish between:
         *
         *
         * "/tmp/" (known safe directory) + "../secret/file.txt" (suspicious looking file name)
         *
         *
         * and
         *
         *
         * "/tmp/../secret/" (known safe directory) + "file.txt" (known safe file name)
         *
         *
         * I guess the best this method offers us is the ability to uniquely trace the different
         * ways in which files are created and handled. It should make it easier to find and
         * prevent suspect usages of methods which only expect SanitizedFile's, but are given
         * a SanitizedFile returned from this method that really originated from user input.
         */
        fun knownSanitized(path: String): SanitizedFile {
            return SanitizedFile(File(path))
        }

        /**
         * @see co.sodalabs.apkupdater.data.SanitizedFile.knownSanitized
         */
        fun knownSanitized(file: File): SanitizedFile {
            return SanitizedFile(file)
        }
    }

    /**
     * The "name" argument is assumed to be a file name, _not including any path separators_.
     * If it is a relative path to be appended to "parent", such as "/blah/sneh.txt", then
     * the forward slashes will be removed and it will be assumed you meant "blahsneh.txt".
     */
    constructor(parent: File, name: String) : super(parent, sanitizeFileName(name))

    /**
     * Used by the [co.sodalabs.apkupdater.data.SanitizedFile.knownSanitized]
     * method, but intentionally kept private so people don't think that any sanitization
     * will occur by passing a file in - because it wont.
     */
    constructor(file: File) : super(file.absolutePath)
}