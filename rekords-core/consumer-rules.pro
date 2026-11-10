# Rekords finds the serializer of a @Rekord or @Embedded class by its KClass (KClass.serializer()),
# which on the JVM reads it by reflection: the Companion field of the class and its serializer().
# The rules kotlinx.serialization ships keep them only for classes annotated with @Serializable,
# while these are serializable through the meta-annotations, so an app minified by R8 would lose
# them: these rules are those of kotlinx.serialization for the two annotations.

# The Companion field of the class.
-if @io.github.denisshakinov.rekords.core.Rekord class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @io.github.denisshakinov.rekords.core.Embedded class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# serializer() of its companion object, named or not.
-if @io.github.denisshakinov.rekords.core.Rekord class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @io.github.denisshakinov.rekords.core.Embedded class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
