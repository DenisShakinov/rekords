package io.github.denisshakinov.rekords.core

@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This rekords API is internal and should not be used. It could be removed or changed without notice."
)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.TYPEALIAS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FIELD,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.PROPERTY_GETTER,
)
annotation class InternalRekordsApi