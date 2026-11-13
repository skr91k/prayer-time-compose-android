# Components are kept automatically from the manifest; storage uses org.json with string keys,
# so no reflection rules are needed. Everything else is shrunk, optimised and obfuscated by R8.
# Keep line numbers so Play crash reports stay readable (mapping.txt is bundled into the AAB).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
