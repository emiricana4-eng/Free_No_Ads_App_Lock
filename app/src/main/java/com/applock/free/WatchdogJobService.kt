package com.applock.free

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

class WatchdogJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        val prefs = PrefManager(this)
        if (prefs.isEnabled && prefs.hasPin()) {
            LockService.start(this)
        }
        jobFinished(params, false)
        return false
    }

    override fun onStopJob(params: JobParameters?) = true

    companion object {
        private const val JOB_ID = 1337

        fun schedule(context: Context) {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            if (scheduler.allPendingJobs.any { it.id == JOB_ID }) return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, WatchdogJobService::class.java))
                .setPeriodic(15 * 60 * 1000L)
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                .build()
            scheduler.schedule(job)
        }
    }
}
