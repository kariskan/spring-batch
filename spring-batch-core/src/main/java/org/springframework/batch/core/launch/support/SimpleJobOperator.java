/*
 * Copyright 2006-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.batch.core.launch.support;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersIncrementer;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.UnexpectedJobExecutionException;
import org.springframework.batch.core.configuration.ListableJobRegistry;
import org.springframework.batch.core.converter.DefaultJobParametersConverter;
import org.springframework.batch.core.converter.JobParametersConverter;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobInstanceAlreadyExistsException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.JobParametersNotFoundException;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.batch.core.launch.NoSuchJobExecutionException;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.batch.support.PropertiesConverter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

/**
 * @author Dave Syer
 * 
 */
public class SimpleJobOperator implements JobOperator, InitializingBean {

	/**
	 * 
	 */
	private static final String ILLEGAL_STATE_MSG = "Illegal state (only happens on a race condition): "
			+ "%s with name=%s and parameters=%s";

	private ListableJobRegistry jobRegistry;

	private JobExplorer jobExplorer;

	private JobLauncher jobLauncher;

	private JobParametersConverter jobParametersConverter = new DefaultJobParametersConverter();

	private final Log logger = LogFactory.getLog(getClass());

	/**
	 * Check mandatory properties.
	 * 
	 * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
	 */
	public void afterPropertiesSet() throws Exception {
		Assert.notNull(jobLauncher, "JobLauncher must be provided");
		Assert.notNull(jobRegistry, "JobLocator must be provided");
		Assert.notNull(jobExplorer, "BatchMetaDataExplorer must be provided");
	}

	/**
	 * Public setter for the {@link JobParametersConverter}.
	 * @param jobParametersConverter the {@link JobParametersConverter} to set
	 */
	public void setJobParametersConverter(JobParametersConverter jobParametersConverter) {
		this.jobParametersConverter = jobParametersConverter;
	}

	/**
	 * Public setter for the {@link ListableJobRegistry}.
	 * @param jobRegistry the {@link ListableJobRegistry} to set
	 */
	public void setJobRegistry(ListableJobRegistry jobRegistry) {
		this.jobRegistry = jobRegistry;
	}

	/**
	 * Public setter for the {@link JobExplorer}.
	 * @param jobExplorer the {@link JobExplorer} to set
	 */
	public void setJobExplorer(JobExplorer jobExplorer) {
		this.jobExplorer = jobExplorer;
	}

	/**
	 * Public setter for the {@link JobLauncher}.
	 * @param jobLauncher the {@link JobLauncher} to set
	 */
	public void setJobLauncher(JobLauncher jobLauncher) {
		this.jobLauncher = jobLauncher;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.springframework.batch.core.launch.JobOperator#getExecutions(java.
	 * lang.Long)
	 */
	public List<Long> getExecutions(Long instanceId) throws NoSuchJobException {
		JobInstance jobInstance = jobExplorer.getJobInstance(instanceId);
		if (jobInstance == null) {
			throw new NoSuchJobException(String.format("No job instance with id=%d", instanceId));
		}
		List<Long> list = new ArrayList<Long>();
		for (JobExecution jobExecution : jobExplorer.findJobExecutions(jobInstance)) {
			list.add(jobExecution.getId());
		}
		return list;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.springframework.batch.core.launch.JobOperator#getJobNames()
	 */
	public Set<String> getJobNames() {
		return new HashSet<String>(jobRegistry.getJobNames());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.springframework.batch.core.launch.JobOperator#getLastInstances(java
	 * .lang.String, int)
	 */
	public List<Long> getLastInstances(String jobName, int count) throws NoSuchJobException {
		List<Long> list = new ArrayList<Long>();
		for (JobInstance jobInstance : jobExplorer.getLastJobInstances(jobName, count)) {
			list.add(jobInstance.getId());
		}
		return list;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.springframework.batch.core.launch.JobOperator#getParameters(java.
	 * lang.Long)
	 */
	public String getParameters(Long executionId) throws NoSuchJobExecutionException {
		JobExecution jobExecution = jobExplorer.getJobExecution(executionId);
		if (jobExecution == null) {
			throw new NoSuchJobExecutionException(String.format("No job execution with id=%d", executionId));
		}
		return PropertiesConverter.propertiesToString(jobParametersConverter.getProperties(jobExecution
				.getJobInstance().getJobParameters()));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.springframework.batch.core.launch.JobOperator#getRunningExecutions
	 * (java.lang.String)
	 */
	public Set<Long> getRunningExecutions(String jobName) throws NoSuchJobException {
		Set<Long> set = new LinkedHashSet<Long>();
		for (JobExecution jobExecution : jobExplorer.findRunningJobExecutions(jobName)) {
			set.add(jobExecution.getId());
		}
		return set;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.springframework.batch.core.launch.JobOperator#getStepExecutionSummaries
	 * (java.lang.Long)
	 */
	public Map<Long, String> getStepExecutionSummaries(Long executionId) throws NoSuchJobExecutionException {
		JobExecution jobExecution = jobExplorer.getJobExecution(executionId);
		if (jobExecution == null) {
			throw new NoSuchJobExecutionException(String.format("No job execution with id=%d", executionId));
		}
		Map<Long, String> map = new LinkedHashMap<Long, String>();
		for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
			map.put(stepExecution.getId(), stepExecution.toString());
		}
		return map;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.springframework.batch.core.launch.JobOperator#getSummary(java.lang
	 * .Long)
	 */
	public String getSummary(Long executionId) throws NoSuchJobExecutionException {
		JobExecution jobExecution = jobExplorer.getJobExecution(executionId);
		if (jobExecution == null) {
			throw new NoSuchJobExecutionException(String.format("No job execution with id=%d", executionId));
		}
		return jobExecution.toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.springframework.batch.core.launch.JobOperator#resume(java.lang.Long)
	 */
	public Long resume(Long executionId) throws JobInstanceAlreadyCompleteException, NoSuchJobExecutionException,
			NoSuchJobException, JobRestartException {

		logger.info("Checking status of job execution with id=" + executionId);

		JobExecution jobExecution = jobExplorer.getJobExecution(executionId);
		if (jobExecution == null) {
			throw new NoSuchJobExecutionException(String.format("No job execution with id=%d", executionId));
		}

		String jobName = jobExecution.getJobInstance().getJobName();
		Job job = jobRegistry.getJob(jobName);
		JobParameters parameters = jobExecution.getJobInstance().getJobParameters();

		logger.info(String.format("Attempting to resume job with name=%s and parameters=%s", jobName, parameters));
		try {
			return jobLauncher.run(job, parameters).getId();
		}
		catch (JobExecutionAlreadyRunningException e) {
			throw new UnexpectedJobExecutionException(String.format(ILLEGAL_STATE_MSG, "job execution already running",
					jobName, parameters), e);
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.springframework.batch.core.launch.JobOperator#start(java.lang.String,
	 * java.lang.String)
	 */
	public Long start(String jobName, String parameters) throws NoSuchJobException, JobInstanceAlreadyExistsException {

		logger.info("Checking status of job with name=" + jobName);

		JobParameters jobParameters = jobParametersConverter.getJobParameters(PropertiesConverter
				.stringToProperties(parameters));

		if (jobExplorer.isJobInstanceExists(jobName, jobParameters)) {
			throw new JobInstanceAlreadyExistsException(String.format(
					"Cannot start a job instance that already exists with name=%s and parameters=%s", jobName,
					parameters));
		}

		Job job = jobRegistry.getJob(jobName);

		logger.info(String.format("Attempting to launch job with name=%s and parameters=%s", jobName, parameters));
		try {
			return jobLauncher.run(job, jobParameters).getId();
		}
		catch (JobExecutionAlreadyRunningException e) {
			throw new UnexpectedJobExecutionException(String.format(ILLEGAL_STATE_MSG, "job execution already running",
					jobName, parameters), e);
		}
		catch (JobRestartException e) {
			throw new UnexpectedJobExecutionException(String.format(ILLEGAL_STATE_MSG, "job not restartable", jobName,
					parameters), e);
		}
		catch (JobInstanceAlreadyCompleteException e) {
			throw new UnexpectedJobExecutionException(String.format(ILLEGAL_STATE_MSG, "job already complete", jobName,
					parameters), e);
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see JobOperator#startNextInstance(String )
	 */
	public Long startNextInstance(String jobName) throws NoSuchJobException, JobParametersNotFoundException,
			UnexpectedJobExecutionException {

		logger.info("Locating parameters for next instance of job with name=" + jobName);

		Job job = jobRegistry.getJob(jobName);
		List<JobInstance> lastInstances = jobExplorer.getLastJobInstances(jobName, 1);

		JobParametersIncrementer incrementer = job.getJobParametersIncrementer();
		if (incrementer == null) {
			throw new JobParametersNotFoundException("No job parameters incrementer found for job=" + jobName);
		}

		JobParameters parameters;
		if (lastInstances.isEmpty()) {
			parameters = incrementer.getNext(new JobParameters());
			if (parameters == null) {
				throw new JobParametersNotFoundException("No bootstrap parameters found for job=" + jobName);
			}
		}
		else {
			parameters = incrementer.getNext(lastInstances.get(0).getJobParameters());
		}

		logger.info(String.format("Attempting to launch job with name=%s and parameters=%s", jobName, parameters));
		try {
			return jobLauncher.run(job, parameters).getId();
		}
		catch (JobExecutionAlreadyRunningException e) {
			throw new UnexpectedJobExecutionException(String.format(ILLEGAL_STATE_MSG, "job already running", jobName,
					parameters), e);
		}
		catch (JobRestartException e) {
			throw new UnexpectedJobExecutionException(String.format(ILLEGAL_STATE_MSG, "job not restartable", jobName,
					parameters), e);
		}
		catch (JobInstanceAlreadyCompleteException e) {
			throw new UnexpectedJobExecutionException(String.format(ILLEGAL_STATE_MSG, "job instance already complete",
					jobName, parameters), e);
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.springframework.batch.core.launch.JobOperator#stop(java.lang.Long)
	 */
	public boolean stop(Long executionId) throws NoSuchJobExecutionException {
		throw new UnsupportedOperationException("See BATCH-453 for implementation.");
	}

}
