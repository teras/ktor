package io.ktor.pipeline

/**
 * Represents running execution of a pipeline
 * @param context object representing context in which pipeline executes
 * @param interceptors list of interceptors to execute
 * @param subject object representing subject that goes along the pipeline
 */
@ContextDsl
class PipelineContext<TSubject : Any, out TContext : Any>(
        val context: TContext,
        subject: TSubject,
        private var interceptors: Iterator<PipelineInterceptor<TSubject, TContext>>?
) {
    /**
     * Subject of this pipeline execution
     */
    var subject: TSubject = subject
        private set

    /**
     * Finishes current pipeline execution
     */
    fun finish() {
        interceptors = null
    }

    /**
     * Continues execution of the pipeline with the given subject
     */
    suspend fun proceedWith(subject: TSubject): TSubject {
        this.subject = subject
        return proceed()
    }

    /**
     * Continues execution of the pipeline with the same subject
     */
    suspend fun proceed(): TSubject {
        while (interceptors != null && interceptors!!.hasNext()) {
            val executeInterceptor = interceptors!!.next()
            executeInterceptor.invoke(this, subject)
        }
        return subject
    }
}
