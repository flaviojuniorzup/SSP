/**
 * Licensed to Apereo under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Apereo licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License.  You may obtain a
 * copy of the License at the following location:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jasig.ssp.service.impl; // NOPMD by jon.adams

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.mail.SendFailedException;
import javax.validation.constraints.NotNull;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.jasig.ssp.config.EarlyAlertResponseReminderRecipientsConfig;
import org.jasig.ssp.dao.EarlyAlertDao;
import org.jasig.ssp.factory.EarlyAlertSearchResultTOFactory;
import org.jasig.ssp.model.EarlyAlert;
import org.jasig.ssp.model.EarlyAlertSearchResult;
import org.jasig.ssp.model.Message;
import org.jasig.ssp.model.ObjectStatus;
import org.jasig.ssp.model.Person;
import org.jasig.ssp.model.PersonProgramStatus;
import org.jasig.ssp.model.SubjectAndBody;
import org.jasig.ssp.model.WatchStudent;
import org.jasig.ssp.model.reference.Campus;
import org.jasig.ssp.model.reference.EarlyAlertReason;
import org.jasig.ssp.model.reference.EarlyAlertSuggestion;
import org.jasig.ssp.model.reference.ProgramStatus;
import org.jasig.ssp.model.reference.StudentType;
import org.jasig.ssp.security.SspUser;
import org.jasig.ssp.service.AbstractPersonAssocAuditableService;
import org.jasig.ssp.service.EarlyAlertService;
import org.jasig.ssp.service.MessageService;
import org.jasig.ssp.service.ObjectNotFoundException;
import org.jasig.ssp.service.PersonProgramStatusService;
import org.jasig.ssp.service.PersonService;
import org.jasig.ssp.service.SecurityService;
import org.jasig.ssp.service.reference.ConfigService;
import org.jasig.ssp.service.reference.EarlyAlertReasonService;
import org.jasig.ssp.service.reference.EarlyAlertSuggestionService;
import org.jasig.ssp.service.reference.MessageTemplateService;
import org.jasig.ssp.service.reference.ProgramStatusService;
import org.jasig.ssp.service.reference.StudentTypeService;
import org.jasig.ssp.transferobject.EarlyAlertSearchResultTO;
import org.jasig.ssp.transferobject.EarlyAlertTO;
import org.jasig.ssp.transferobject.PagedResponse;
import org.jasig.ssp.transferobject.form.EarlyAlertSearchForm;
import org.jasig.ssp.transferobject.messagetemplate.EarlyAlertMessageTemplateTO;
import org.jasig.ssp.transferobject.reports.EarlyAlertCourseCountsTO;
import org.jasig.ssp.transferobject.reports.EarlyAlertReasonCountsTO;
import org.jasig.ssp.transferobject.reports.EarlyAlertStudentReportTO;
import org.jasig.ssp.transferobject.reports.EarlyAlertStudentSearchTO;
import org.jasig.ssp.transferobject.reports.EntityCountByCoachSearchForm;
import org.jasig.ssp.transferobject.reports.EntityStudentCountByCoachTO;
import org.jasig.ssp.util.DateTimeUtils;
import org.jasig.ssp.util.collections.Pair;
import org.jasig.ssp.util.collections.Triple;
import org.jasig.ssp.util.sort.PagingWrapper;
import org.jasig.ssp.util.sort.SortingAndPaging;
import org.jasig.ssp.web.api.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.collect.Lists;

/**
 * EarlyAlert service implementation
 * 
 * @author jon.adams
 * 
 */
@Service
@Transactional
public class EarlyAlertServiceImpl extends // NOPMD
		AbstractPersonAssocAuditableService<EarlyAlert> implements EarlyAlertService {

	@Autowired
	private transient EarlyAlertDao dao;
	@Autowired
	private transient ConfigService configService;
	@Autowired
	private transient MessageService messageService;
	@Autowired
	private transient MessageTemplateService messageTemplateService;
	@Autowired
	private transient EarlyAlertReasonService earlyAlertReasonService;
	@Autowired
	private transient EarlyAlertSuggestionService earlyAlertSuggestionService;
	@Autowired
	private transient PersonService personService;
	@Autowired
	private transient PersonProgramStatusService personProgramStatusService;
	@Autowired
	private transient ProgramStatusService programStatusService;
	@Autowired
	private transient StudentTypeService studentTypeService;
	@Autowired
	private transient SecurityService securityService;
	@Autowired
	private transient EarlyAlertSearchResultTOFactory searchResultFactory;
	@Autowired
	private transient EarlyAlertResponseReminderRecipientsConfig earReminderRecipientConfig;

	@Autowired
	private EarlyAlertMessageService earlyAlertMessageService;

	private static final Logger LOGGER = LoggerFactory.getLogger(EarlyAlertServiceImpl.class);

	@Override
	protected EarlyAlertDao getDao() {
		return dao;
	}

	@Override
	@Transactional(rollbackFor = { ObjectNotFoundException.class, ValidationException.class })
	public EarlyAlert create(@NotNull final EarlyAlert earlyAlert) throws ObjectNotFoundException, ValidationException {
		checkIsValidForCreate(earlyAlert);

		// Figure student advisor or early alert coordinator
		final UUID assignedAdvisor = getEarlyAlertAdvisor(earlyAlert);
		final Person student = earlyAlert.getPerson();
		if (student.getCoach() == null || assignedAdvisor.equals(student.getCoach().getId())) {
			student.setCoach(personService.get(assignedAdvisor));
		}

		ensureValidAlertedOnPersonStateNoFail(student);

		// Create alert
		final EarlyAlert saved = getDao().save(earlyAlert);
		
		postCreated(saved);

		return saved;
	}

	private void postCreated(EarlyAlert saved) throws ObjectNotFoundException, ValidationException {
		// Send e-mail to assigned advisor (coach)
		try {
			sendMessageToAdvisor(saved, saved.getEmailCC());
		} catch (final SendFailedException e) {
			LOGGER.warn("Could not send Early Alert message to advisor.", e);
			throw new ValidationException(
					"Early Alert notification e-mail could not be sent to advisor. Early Alert was NOT created.", e);
		}

		// Send e-mail CONFIRMATION to faculty
		try {
			sendConfirmationMessageToFaculty(saved);
		} catch (final SendFailedException e) {
			LOGGER.warn("Could not send Early Alert confirmation to faculty.", e);
			throw new ValidationException(
					"Early Alert confirmation e-mail could not be sent. Early Alert was NOT created.", e);
		}
		
	}

	private void checkIsValidForCreate(@NotNull final EarlyAlert earlyAlert) throws ValidationException {
		// Validate objects
		Validate.notNull(earlyAlert.getPerson(), "EarlyAlert Student data must be provided.");

		final Person student = earlyAlert.getPerson();

		// Figure student advisor or early alert coordinator
		final UUID assignedAdvisor = getEarlyAlertAdvisor(earlyAlert);
		if (assignedAdvisor == null) {
			throw new ValidationException(
					"Could not determine the Early Alert Advisor for student ID " + student.getId());
		}

	}

	@Override
	public void closeEarlyAlert(UUID earlyAlertId) throws ObjectNotFoundException, ValidationException {
		final EarlyAlert earlyAlert = getDao().get(earlyAlertId);

		// DAOs don't implement ObjectNotFoundException consistently and we'd
		// rather they not implement it at all, so a small attempt at 'future
		// proofing' here
		if (earlyAlert == null) {
			throw new ObjectNotFoundException(earlyAlertId, EarlyAlert.class.getName());
		}

		if (earlyAlert.getClosedDate() != null) {
			// already closed
			return;
		}

		final SspUser sspUser = securityService.currentUser();
		if (sspUser == null) {
			throw new ValidationException("Early Alert cannot be closed by a null User.");
		}

		earlyAlert.setClosedDate(new Date());
		earlyAlert.setClosedBy(sspUser.getPerson());

		// This save will result in a Hib session flush, which works fine with
		// our current usage. Future use cases might prefer to delay the
		// flush and we can address that when the time comes. Might not even
		// need to change anything here if it turns out nothing actually
		// *depends* on the flush.
		getDao().save(earlyAlert);
	}

	@Override
	public void openEarlyAlert(UUID earlyAlertId) throws ObjectNotFoundException, ValidationException {
		final EarlyAlert earlyAlert = getDao().get(earlyAlertId);

		// DAOs don't implement ObjectNotFoundException consistently and we'd
		// rather they not implement it at all, so a small attempt at 'future
		// proofing' here
		if (earlyAlert == null) {
			throw new ObjectNotFoundException(earlyAlertId, EarlyAlert.class.getName());
		}

		if (earlyAlert.getClosedDate() == null) {
			return;
		}

		final SspUser sspUser = securityService.currentUser();
		if (sspUser == null) {
			throw new ValidationException("Early Alert cannot be closed by a null User.");
		}

		earlyAlert.setClosedDate(null);
		earlyAlert.setClosedBy(null);

		// This save will result in a Hib session flush, which works fine with
		// our current usage. Future use cases might prefer to delay the
		// flush and we can address that when the time comes. Might not even
		// need to change anything here if it turns out nothing actually
		// *depends* on the flush.
		getDao().save(earlyAlert);
	}

	@Override
	public EarlyAlert save(@NotNull final EarlyAlert obj) throws ObjectNotFoundException {
		final EarlyAlert current = getDao().get(obj.getId());

		current.setCourseName(obj.getCourseName());
		current.setCourseTitle(obj.getCourseTitle());
		current.setEmailCC(obj.getEmailCC());
		current.setCampus(obj.getCampus());
		current.setEarlyAlertReasonOtherDescription(obj.getEarlyAlertReasonOtherDescription());
		current.setComment(obj.getComment());
		current.setClosedDate(obj.getClosedDate());
		if (obj.getClosedById() == null) {
			current.setClosedBy(null);
		} else {
			current.setClosedBy(personService.get(obj.getClosedById()));
		}

		if (obj.getPerson() == null) {
			current.setPerson(null);
		} else {
			current.setPerson(personService.get(obj.getPerson().getId()));
		}

		final Set<EarlyAlertReason> earlyAlertReasons = new HashSet<EarlyAlertReason>();
		if (obj.getEarlyAlertReasons() != null) {
			for (final EarlyAlertReason reason : obj.getEarlyAlertReasons()) {
				earlyAlertReasons.add(earlyAlertReasonService.load(reason.getId()));
			}
		}

		current.setEarlyAlertReasons(earlyAlertReasons);

		final Set<EarlyAlertSuggestion> earlyAlertSuggestions = new HashSet<EarlyAlertSuggestion>();
		if (obj.getEarlyAlertSuggestions() != null) {
			for (final EarlyAlertSuggestion reason : obj.getEarlyAlertSuggestions()) {
				earlyAlertSuggestions.add(earlyAlertSuggestionService.load(reason.getId()));
			}
		}

		current.setEarlyAlertSuggestions(earlyAlertSuggestions);

		return getDao().save(current);
	}

	@Override
	public PagingWrapper<EarlyAlert> getAllForPerson(final Person person, final SortingAndPaging sAndP) {
		return getDao().getAllForPersonId(person.getId(), sAndP);
	}

	/**
	 * Business logic to determine the advisor that is assigned to the student for
	 * this Early Alert.
	 * 
	 * @param earlyAlert EarlyAlert instance
	 * @throws ValidationException If Early Alert, Student, and/or system
	 *                             information could not determine the advisor for
	 *                             this student.
	 * @return The assigned advisor
	 */
	private UUID getEarlyAlertAdvisor(final EarlyAlert earlyAlert) throws ValidationException {
		// Check for student already assigned to an advisor (a.k.a. coach)
		if ((earlyAlert.getPerson().getCoach() != null) && (earlyAlert.getPerson().getCoach().getId() != null)) {
			return earlyAlert.getPerson().getCoach().getId();
		}

		// Get campus Early Alert coordinator
		if (earlyAlert.getCampus() == null) {
			throw new IllegalArgumentException("Campus ID can not be null.");
		}

		if (earlyAlert.getCampus().getEarlyAlertCoordinatorId() != null) {
			// Return Early Alert coordinator UUID
			return earlyAlert.getCampus().getEarlyAlertCoordinatorId();
		}

		// TODO If no campus EA Coordinator, assign to default EA Coordinator
		// (which is not yet implemented)

		// getEarlyAlertAdvisor should never return null
		throw new ValidationException(
				"Could not determined the Early Alert Coordinator for this student. Ensure that a default coordinator is set globally and for all campuses.");
	}

	private void ensureValidAlertedOnPersonStateNoFail(Person person) {
		try {
			ensureValidAlertedOnPersonStateOrFail(person);
		} catch (Exception e) {
			LOGGER.error(
					"Unable to set a program status or student type on "
							+ "person '{}'. This is likely to prevent that person "
							+ "record from appearing in caseloads, student searches, " + "and some reports.",
					person.getId(), e);
		}
	}

	private void ensureValidAlertedOnPersonStateOrFail(Person person)
			throws ObjectNotFoundException, ValidationException {

		if (person.getObjectStatus() != ObjectStatus.ACTIVE) {
			person.setObjectStatus(ObjectStatus.ACTIVE);
		}

		final ProgramStatus programStatus = programStatusService.getActiveStatus();
		if (programStatus == null) {
			throw new ObjectNotFoundException("Unable to find a ProgramStatus representing \"activeness\".",
					"ProgramStatus");
		}

		Set<PersonProgramStatus> programStatuses = person.getProgramStatuses();
		if (programStatuses == null || programStatuses.isEmpty()) {
			PersonProgramStatus personProgramStatus = new PersonProgramStatus();
			personProgramStatus.setEffectiveDate(new Date());
			personProgramStatus.setProgramStatus(programStatus);
			personProgramStatus.setPerson(person);
			programStatuses.add(personProgramStatus);
			person.setProgramStatuses(programStatuses);
			// save should cascade, but make sure custom create logic fires
			personProgramStatusService.create(personProgramStatus);
		}

		if (person.getStudentType() == null) {
			StudentType studentType = studentTypeService.get(StudentType.EAL_ID);
			if (studentType == null) {
				throw new ObjectNotFoundException(
						"Unable to find a StudentType representing an early " + "alert-assigned type.", "StudentType");
			}
			person.setStudentType(studentType);
		}
	}

	/**
	 * Send e-mail ({@link Message}) to the assigned advisor for the student.
	 * 
	 * @param earlyAlert Early Alert
	 * @param emailCC    Email address to also CC this message
	 * @throws ObjectNotFoundException
	 * @throws SendFailedException
	 * @throws ValidationException
	 */
	private void sendMessageToAdvisor(@NotNull final EarlyAlert earlyAlert, // NOPMD
			final String emailCC) throws ObjectNotFoundException, SendFailedException, ValidationException {
		earlyAlertMessageService.sendMessageToAdvisor(earlyAlert, emailCC);
	}

	@Override
	public void sendMessageToStudent(@NotNull final EarlyAlert earlyAlert)
			throws ObjectNotFoundException, SendFailedException, ValidationException {
		if (earlyAlert == null) {
			throw new IllegalArgumentException("EarlyAlert was missing.");
		}

		if (earlyAlert.getPerson() == null) {
			throw new IllegalArgumentException("EarlyAlert.Person is missing.");
		}

		final Person person = earlyAlert.getPerson();
		final SubjectAndBody subjAndBody = messageTemplateService
				.createEarlyAlertToStudentMessage(fillTemplateParameters(earlyAlert));

		Set<String> watcheremails = new HashSet<String>(person.getWatcherEmailAddresses());
		// Create and queue the message
		final Message message = messageService.createMessage(person, org.springframework.util.StringUtils
				.arrayToCommaDelimitedString(watcheremails.toArray(new String[watcheremails.size()])), subjAndBody);

		LOGGER.info("Message {} created for EarlyAlert {}", message, earlyAlert);
	}

	/**
	 * Send confirmation e-mail ({@link Message}) to the faculty who created this
	 * alert.
	 * 
	 * @param earlyAlert Early Alert
	 * @throws ObjectNotFoundException
	 * @throws SendFailedException
	 * @throws ValidationException
	 */
	private void sendConfirmationMessageToFaculty(final EarlyAlert earlyAlert)
			throws ObjectNotFoundException, SendFailedException, ValidationException {
		earlyAlertMessageService.sendConfirmationMessageToFaculty(earlyAlert);
	}

	@Override
	public Map<String, Object> fillTemplateParameters(@NotNull final EarlyAlert earlyAlert) {
		return earlyAlertMessageService.fillTemplateParameters(earlyAlert);
	}

	@Override
	public void applyEarlyAlertCounts(Person person) {
		if (person == null) {
			return; // can occur in some legit person lookup call paths
		}
	}

	@Override
	public Map<UUID, Number> getCountOfActiveAlertsForPeopleIds(final Collection<UUID> peopleIds) {
		return dao.getCountOfActiveAlertsForPeopleIds(peopleIds);
	}

	@Override
	public Map<UUID, Number> getCountOfClosedAlertsForPeopleIds(final Collection<UUID> peopleIds) {
		return dao.getCountOfClosedAlertsForPeopleIds(peopleIds);
	}

	@Override
	public Long getCountOfEarlyAlertsForSchoolIds(final Collection<String> schoolIds, Campus campus) {
		return dao.getCountOfAlertsForSchoolIds(schoolIds, campus);
	}

	@Override
	public Long getEarlyAlertCountForCoach(Person coach, Date createDateFrom, Date createDateTo,
			List<UUID> studentTypeIds) {
		return dao.getEarlyAlertCountForCoach(coach, createDateFrom, createDateTo, studentTypeIds);
	}

	@Override
	public Long getStudentEarlyAlertCountForCoach(Person coach, Date createDateFrom, Date createDateTo,
			List<UUID> studentTypeIds) {
		return dao.getStudentEarlyAlertCountForCoach(coach, createDateFrom, createDateTo, studentTypeIds);
	}

	@Override
	public Long getEarlyAlertCountForCreatedDateRange(String termCode, Date createDatedFrom, Date createdDateTo,
			Campus campus, String rosterStatus) {
		return dao.getEarlyAlertCountForCreatedDateRange(termCode, createDatedFrom, createdDateTo, campus,
				rosterStatus);
	}

	@Override
	public Long getClosedEarlyAlertCountForClosedDateRange(Date closedDateFrom, Date closedDateTo, Campus campus,
			String rosterStatus) {
		return dao.getClosedEarlyAlertCountForClosedDateRange(closedDateFrom, closedDateTo, campus, rosterStatus);
	}

	@Override
	public Long getClosedEarlyAlertsCountForEarlyAlertCreatedDateRange(String termCode, Date createDatedFrom,
			Date createdDateTo, Campus campus, String rosterStatus) {
		return dao.getClosedEarlyAlertsCountForEarlyAlertCreatedDateRange(termCode, createDatedFrom, createdDateTo,
				campus, rosterStatus);
	}

	@Override
	public Long getStudentCountForEarlyAlertCreatedDateRange(String termCode, Date createDatedFrom, Date createdDateTo,
			Campus campus, String rosterStatus) {
		return dao.getStudentCountForEarlyAlertCreatedDateRange(termCode, createDatedFrom, createdDateTo, campus,
				rosterStatus);
	}

	@Override
	public PagingWrapper<EarlyAlertStudentReportTO> getStudentsEarlyAlertCountSetForCriteria(
			EarlyAlertStudentSearchTO earlyAlertStudentSearchTO, SortingAndPaging createForSingleSort) {
		return dao.getStudentsEarlyAlertCountSetForCriteria(earlyAlertStudentSearchTO, createForSingleSort);
	}

	@Override
	public List<EarlyAlertCourseCountsTO> getStudentEarlyAlertCountSetPerCourses(String termCode, Date createdDateFrom,
			Date createdDateTo, Campus campus, ObjectStatus objectStatus) {
		return dao.getStudentEarlyAlertCountSetPerCourses(termCode, createdDateFrom, createdDateTo, campus,
				objectStatus);
	}

	@Override
	public Long getStudentEarlyAlertCountSetPerCoursesTotalStudents(String termCode, Date createdDateFrom,
			Date createdDateTo, Campus campus, ObjectStatus objectStatus) {
		return dao.getStudentEarlyAlertCountSetPerCoursesTotalStudents(termCode, createdDateFrom, createdDateTo, campus,
				objectStatus);
	}

	@Override
	public List<Triple<String, Long, Long>> getEarlyAlertReasonTypeCountByCriteria(Campus campus, String termCode,
			Date createdDateFrom, Date createdDateTo, ObjectStatus status) {
		return dao.getEarlyAlertReasonTypeCountByCriteria(campus, termCode, createdDateFrom, createdDateTo, status);
	}

	@Override
	public List<EarlyAlertReasonCountsTO> getStudentEarlyAlertReasonCountByCriteria(String termCode,
			Date createdDateFrom, Date createdDateTo, Campus campus, ObjectStatus objectStatus) {
		return dao.getStudentEarlyAlertReasonCountByCriteria(termCode, createdDateFrom, createdDateTo, campus,
				objectStatus);
	}

	@Override
	public Long getStudentEarlyAlertReasonCountByCriteriaTotalStudents(String termCode, Date createdDateFrom,
			Date createdDateTo, Campus campus, ObjectStatus objectStatus) {
		return dao.getStudentEarlyAlertReasonCountByCriteriaTotalStudents(termCode, createdDateFrom, createdDateTo,
				campus, objectStatus);
	}

	@Override
	public PagingWrapper<EntityStudentCountByCoachTO> getStudentEarlyAlertCountByCoaches(
			EntityCountByCoachSearchForm form) {
		return dao.getStudentEarlyAlertCountByCoaches(form);
	}

	@Override
	public Long getEarlyAlertCountSetForCriteria(EarlyAlertStudentSearchTO searchForm) {
		return dao.getEarlyAlertCountSetForCriteria(searchForm);
	}

	@Override
	public void sendAllEarlyAlertReminderNotifications() {
		Date lastResponseDate = getMinimumResponseComplianceDate();
		// if no responseDate is given no emails are sent
		if (lastResponseDate == null) {
			return;
		}
		List<EarlyAlert> eaOutOfCompliance = dao.getResponseDueEarlyAlerts(lastResponseDate);

		Map<UUID, List<EarlyAlertMessageTemplateTO>> easByCoach = new HashMap<UUID, List<EarlyAlertMessageTemplateTO>>();
		Map<UUID, Person> coaches = new HashMap<UUID, Person>();
		final boolean includeCoachAsRecipient = this.earReminderRecipientConfig.includeCoachAsRecipient();
		final boolean includeEarlyAlertCoordinatorAsRecipient = this.earReminderRecipientConfig
				.includeEarlyAlertCoordinatorAsRecipient();
		final boolean includeEarlyAlertCoordinatorAsRecipientOnlyIfStudentHasNoCoach = this.earReminderRecipientConfig
				.includeEarlyAlertCoordinatorAsRecipientOnlyIfStudentHasNoCoach();
		LOGGER.info("Config: includeCoachAsRecipient(): {}", includeCoachAsRecipient);
		LOGGER.info("Config: includeEarlyAlertCoordinatorAsRecipient(): {}", includeEarlyAlertCoordinatorAsRecipient);
		LOGGER.info("Config: includeEarlyAlertCoordinatorAsRecipientOnlyIfStudentHasNoCoach(): {}",
				includeEarlyAlertCoordinatorAsRecipientOnlyIfStudentHasNoCoach);
		for (EarlyAlert earlyAlert : eaOutOfCompliance) {
			final Set<Person> recipients = new HashSet<Person>();
			Person coach = earlyAlert.getPerson().getCoach();
			if (includeCoachAsRecipient) {
				if (coach == null) {
					LOGGER.warn(
							"Early Alert with id: {} is associated with a person without a coach, so skipping email to coach.",
							earlyAlert.getId());
				} else {
					recipients.add(coach);
				}
			}
			if (includeEarlyAlertCoordinatorAsRecipient
					|| (coach == null && includeEarlyAlertCoordinatorAsRecipientOnlyIfStudentHasNoCoach)) {
				final Campus campus = earlyAlert.getCampus();
				if (campus == null) {
					LOGGER.error("Early Alert with id: {} does not have valid a campus, so skipping email to EAC.",
							earlyAlert.getId());
				} else {
					final UUID earlyAlertCoordinatorId = campus.getEarlyAlertCoordinatorId();
					if (earlyAlertCoordinatorId == null) {
						LOGGER.error(
								"Early Alert with id: {} has campus with no early alert coordinator, so skipping email to EAC.",
								earlyAlert.getId());
					} else {
						try {
							final Person earlyAlertCoordinator = personService.get(earlyAlertCoordinatorId);
							if (earlyAlertCoordinator == null) { // guard against change in behavior where
																	// ObjectNotFoundException is not thrown (which
																	// we've seen)
								LOGGER.error(
										"Early Alert with id: {} has campus with an early alert coordinator with a bad ID ({}), so skipping email to EAC.",
										earlyAlert.getId(), earlyAlertCoordinatorId);
							} else {
								recipients.add(earlyAlertCoordinator);
							}
						} catch (ObjectNotFoundException exp) {
							LOGGER.error(
									"Early Alert with id: {} has campus with an early alert coordinator with a bad ID ({}), so skipping email to coach because no coach can be resolved.",
									new Object[] { earlyAlert.getId(), earlyAlertCoordinatorId, exp });
						}
					}
				}
			}
			LOGGER.debug("Early Alert: {}; Recipients: {}", earlyAlert.getId(), recipients);
			if (recipients.isEmpty()) {
				continue;
			} else {
				for (Person person : recipients) {
					// We've definitely got a coach by this point
					if (easByCoach.containsKey(person.getId())) {
						final List<EarlyAlertMessageTemplateTO> coachEarlyAlerts = easByCoach.get(person.getId());
						coachEarlyAlerts.add(createEarlyAlertTemplateTO(earlyAlert));
					} else {
						coaches.put(person.getId(), person);
						final ArrayList<EarlyAlertMessageTemplateTO> eam = Lists.newArrayList();
						eam.add(createEarlyAlertTemplateTO(earlyAlert)); // add separately from newArrayList() call else
																			// list will be sized to 1
						easByCoach.put(person.getId(), eam);
					}
				}
			}
			List<WatchStudent> watchers = earlyAlert.getPerson().getWatchers();
			for (WatchStudent watcher : watchers) {
				if (easByCoach.containsKey(watcher.getPerson().getId())) {
					final List<EarlyAlertMessageTemplateTO> coachEarlyAlerts = easByCoach
							.get(watcher.getPerson().getId());
					coachEarlyAlerts.add(createEarlyAlertTemplateTO(earlyAlert));
				} else {
					coaches.put(watcher.getPerson().getId(), watcher.getPerson());
					final ArrayList<EarlyAlertMessageTemplateTO> eam = Lists.newArrayList();
					eam.add(createEarlyAlertTemplateTO(earlyAlert)); // add separately from newArrayList() call else
																		// list will be sized to 1
					easByCoach.put(watcher.getPerson().getId(), eam);
				}
			}
		}
		for (UUID coachId : easByCoach.keySet()) {
			Map<String, Object> messageParams = new HashMap<String, Object>();

			Collections.sort(easByCoach.get(coachId), new Comparator<EarlyAlertTO>() {
				@Override
				public int compare(EarlyAlertTO p1, EarlyAlertTO p2) {
					Date p1Date = p1.getLastResponseDate();
					if (p1Date == null)
						p1Date = p1.getCreatedDate();
					Date p2Date = p2.getLastResponseDate();
					if (p2Date == null)
						p2Date = p2.getCreatedDate();
					return p1Date.compareTo(p2Date);
				}

			});

			Integer daysSince1900ResponseExpected = DateTimeUtils.daysSince1900(lastResponseDate);
			List<Pair<EarlyAlertMessageTemplateTO, Integer>> earlyAlertTOPairs = new ArrayList<Pair<EarlyAlertMessageTemplateTO, Integer>>();
			for (EarlyAlertMessageTemplateTO ea : easByCoach.get(coachId)) {
				Integer daysOutOfCompliance;
				if (ea.getLastResponseDate() != null) {
					daysOutOfCompliance = daysSince1900ResponseExpected
							- DateTimeUtils.daysSince1900(ea.getLastResponseDate());
				} else {
					daysOutOfCompliance = daysSince1900ResponseExpected
							- DateTimeUtils.daysSince1900(ea.getCreatedDate());
				}

				// Just in case attempt to only send emails for EA full day out of compliance
				if (daysOutOfCompliance >= 0)
					earlyAlertTOPairs.add(new Pair<EarlyAlertMessageTemplateTO, Integer>(ea, daysOutOfCompliance));
			}
			messageParams.put("earlyAlertTOPairs", earlyAlertTOPairs);
			messageParams.put("coach", coaches.get(coachId));
			messageParams.put("DateTimeUtils", DateTimeUtils.class);
			messageParams.put("termToRepresentEarlyAlert",
					configService.getByNameEmpty("term_to_represent_early_alert"));

			SubjectAndBody subjAndBody = messageTemplateService
					.createEarlyAlertResponseRequiredToCoachMessage(messageParams);
			try {
				messageService.createMessage(coaches.get(coachId), null, subjAndBody);
			} catch (Exception exp) {
				LOGGER.error("Unable to send reminder emails to coach: " + coaches.get(coachId).getFullName() + "\n",
						exp);
			}
		}

	}

	private EarlyAlertMessageTemplateTO createEarlyAlertTemplateTO(EarlyAlert earlyAlert) {
		Person creator = null;
		try {
			creator = personService.get(earlyAlert.getCreatedBy().getId());
		} catch (ObjectNotFoundException exp) {
			LOGGER.error("Early Alert with id: " + earlyAlert.getId() + " does not have valid creator: "
					+ earlyAlert.getCreatedBy(), exp);
		}
		return new EarlyAlertMessageTemplateTO(earlyAlert, creator, earlyAlert.getPerson().getWatcherEmailAddresses());
	}

	public Map<UUID, Number> getResponsesDueCountEarlyAlerts(List<UUID> personIds) {
		Date lastResponseDate = getMinimumResponseComplianceDate();
		if (lastResponseDate == null)
			return new HashMap<UUID, Number>();
		return dao.getResponsesDueCountEarlyAlerts(personIds, lastResponseDate);
	}

	private Date getMinimumResponseComplianceDate() {
		final String numVal = configService.getByNameNull("maximum_days_before_early_alert_response");
		if (StringUtils.isBlank(numVal))
			return null;
		Integer allowedDaysPastResponse = Integer.parseInt(numVal);

		return DateTimeUtils.getDateOffsetInDays(new Date(), -allowedDaysPastResponse);

	}

	@Override
	public PagedResponse<EarlyAlertSearchResultTO> searchEarlyAlert(EarlyAlertSearchForm form) {
		PagingWrapper<EarlyAlertSearchResult> models = dao.searchEarlyAlert(form);
		return new PagedResponse<EarlyAlertSearchResultTO>(true, models.getResults(),
				searchResultFactory.asTOList(models.getRows()));
	}
}
