package org.jasig.ssp.service.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.mail.SendFailedException;
import javax.validation.constraints.NotNull;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.jasig.ssp.model.EarlyAlert;
import org.jasig.ssp.model.EarlyAlertRouting;
import org.jasig.ssp.model.Message;
import org.jasig.ssp.model.ObjectStatus;
import org.jasig.ssp.model.Person;
import org.jasig.ssp.model.SubjectAndBody;
import org.jasig.ssp.model.external.FacultyCourse;
import org.jasig.ssp.model.external.Term;
import org.jasig.ssp.model.reference.EnrollmentStatus;
import org.jasig.ssp.service.EarlyAlertRoutingService;
import org.jasig.ssp.service.MessageService;
import org.jasig.ssp.service.ObjectNotFoundException;
import org.jasig.ssp.service.PersonService;
import org.jasig.ssp.service.external.FacultyCourseService;
import org.jasig.ssp.service.external.TermService;
import org.jasig.ssp.service.reference.ConfigService;
import org.jasig.ssp.service.reference.EnrollmentStatusService;
import org.jasig.ssp.service.reference.MessageTemplateService;
import org.jasig.ssp.transferobject.messagetemplate.CoachPersonLiteMessageTemplateTO;
import org.jasig.ssp.transferobject.messagetemplate.EarlyAlertMessageTemplateTO;
import org.jasig.ssp.util.sort.PagingWrapper;
import org.jasig.ssp.util.sort.SortingAndPaging;
import org.jasig.ssp.web.api.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class EarlyAlertMessageService {

	@Autowired
	private transient EarlyAlertRoutingService earlyAlertRoutingService;
	@Autowired
	private transient MessageService messageService;
	@Autowired
	private transient MessageTemplateService messageTemplateService;
	@Autowired
	private transient FacultyCourseService facultyCourseService;
	@Autowired
	private transient TermService termService;
	@Autowired
	private transient EnrollmentStatusService enrollmentStatusService;
	@Autowired
	private transient PersonService personService;
	@Autowired
	private transient ConfigService configService;

	private static final Logger LOGGER = LoggerFactory.getLogger(EarlyAlertMessageService.class);

	/**
	 * Send e-mail ({@link Message}) to the assigned advisor for the student.
	 * 
	 * @param earlyAlert Early Alert
	 * @param emailCC    Email address to also CC this message
	 * @throws ObjectNotFoundException
	 * @throws SendFailedException
	 * @throws ValidationException
	 */
	public void sendMessageToAdvisor(@NotNull final EarlyAlert earlyAlert, // NOPMD
			final String emailCC) throws ObjectNotFoundException, SendFailedException, ValidationException {

		Validate.notNull(earlyAlert.getPerson(), "EarlyAlert Person is missing.");

		final Person person = earlyAlert.getPerson().getCoach();
		final SubjectAndBody subjAndBody = messageTemplateService
				.createEarlyAlertAdvisorConfirmationMessage(fillTemplateParameters(earlyAlert));

		Set<String> watcherEmailAddresses = new HashSet<String>(earlyAlert.getPerson().getWatcherEmailAddresses());
		if (emailCC != null && !emailCC.isEmpty()) {
			watcherEmailAddresses.add(emailCC);
		}
		if (person == null) {
			LOGGER.warn("Student {} had no coach when EarlyAlert {} was" + " created. Unable to send message to coach.",
					earlyAlert.getPerson(), earlyAlert);
		} else {
			// Create and queue the message
			final Message message = messageService
					.createMessage(person,
							org.springframework.util.StringUtils.arrayToCommaDelimitedString(
									watcherEmailAddresses.toArray(new String[watcherEmailAddresses.size()])),
							subjAndBody);
			LOGGER.info("Message {} created for EarlyAlert {}", message, earlyAlert);
		}

		// Send same message to all applicable Campus Early Alert routing
		// entries
		final PagingWrapper<EarlyAlertRouting> routes = earlyAlertRoutingService.getAllForCampus(earlyAlert.getCampus(),
				new SortingAndPaging(ObjectStatus.ACTIVE));

		if (routes.getResults() > 0) {
			final ArrayList<String> alreadySent = Lists.newArrayList();

			for (final EarlyAlertRouting route : routes.getRows()) {
				sendMessageToGroup(earlyAlert, alreadySent, route, subjAndBody);
			}
		}
	}

	private void sendMessageToGroup(@NotNull final EarlyAlert earlyAlert, ArrayList<String> alreadySent,
			EarlyAlertRouting route, SubjectAndBody subjAndBody) throws ObjectNotFoundException, ValidationException {
		// Check that route applies
		if (route.getEarlyAlertReason() == null) {
			throw new ObjectNotFoundException("EarlyAlertRouting missing EarlyAlertReason.", "EarlyAlertReason");
		}

		// Only routes that are for any of the Reasons in this EarlyAlert should be
		// applied.
		if ((earlyAlert.getEarlyAlertReasons() == null)
				|| !earlyAlert.getEarlyAlertReasons().contains(route.getEarlyAlertReason())) {
			return;
		}

		// Send e-mail to specific person
		final Person to = route.getPerson();
		if (to != null && StringUtils.isNotBlank(to.getPrimaryEmailAddress())) {
			// check if this alert has already been sent to this recipient, if so skip
			if (alreadySent.contains(route.getPerson().getPrimaryEmailAddress())) {
				return;
			} else {
				alreadySent.add(route.getPerson().getPrimaryEmailAddress());
			}

			final Message message = messageService.createMessage(to, null, subjAndBody);
			LOGGER.info("Message {} for EarlyAlert {} also routed to {}", new Object[] { message, earlyAlert, to }); // NOPMD
		}

		// Send e-mail to a group
		if (!StringUtils.isEmpty(route.getGroupName()) && !StringUtils.isEmpty(route.getGroupEmail())) {
			final Message message = messageService.createMessage(route.getGroupEmail(), null, subjAndBody);
			LOGGER.info("Message {} for EarlyAlert {} also routed to {}", new Object[] { message, earlyAlert, // NOPMD
					route.getGroupEmail() });
		}
	}

	public Map<String, Object> fillTemplateParameters(@NotNull final EarlyAlert earlyAlert) {
		Validate.notNull(earlyAlert.getPerson(), "EarlyAlert.Person is missing.");

		Validate.notNull(earlyAlert.getCreatedBy(), "EarlyAlert.CreatedBy is missing.");

		Validate.notNull(earlyAlert.getCampus(), "EarlyAlert.Campus is missing.");

		// ensure earlyAlert.createdBy is populated
		Validate.notNull(earlyAlert.getCreatedBy(), "EarlyAlert.CreatedBy is missing.");

		final Map<String, Object> templateParameters = Maps.newHashMap();

		final String courseName = earlyAlert.getCourseName();
		if (StringUtils.isNotBlank(courseName)) {
			Person creator;
			try {
				creator = personService.get(earlyAlert.getCreatedBy().getId());
			} catch (ObjectNotFoundException e1) {
				throw new IllegalArgumentException("EarlyAlert.CreatedBy.Id could not be loaded.", e1);
			}
			final String facultySchoolId = creator.getSchoolId();
			if ((StringUtils.isNotBlank(facultySchoolId))) {
				String termCode = earlyAlert.getCourseTermCode();
				FacultyCourse course = null;
				try {
					if (StringUtils.isBlank(termCode)) {
						course = facultyCourseService.getCourseByFacultySchoolIdAndFormattedCourse(facultySchoolId,
								courseName);
					} else {
						course = facultyCourseService.getCourseByFacultySchoolIdAndFormattedCourseAndTermCode(
								facultySchoolId, courseName, termCode);
					}
				} catch (ObjectNotFoundException e) {
					// Trace irrelevant. see below for logging. prefer to
					// do it there, after the null check b/c not all service
					// methods implement ObjectNotFoundException reliably.
				}
				if (course != null) {
					templateParameters.put("course", course);
					if (StringUtils.isBlank(termCode)) {
						termCode = course.getTermCode();
					}
					if (StringUtils.isNotBlank(termCode)) {
						Term term = null;
						try {
							term = termService.getByCode(termCode);
							templateParameters.put("term", term);
						} catch (ObjectNotFoundException e) {
							LOGGER.info(
									"Not adding term to message template" + " params or early alert {} because"
											+ " the term code {} did not resolve to" + " an external term record",
									earlyAlert.getId(), termCode);
						}
					}
				} else {
					LOGGER.info("Not adding course nor term to message template"
							+ " params for early alert {} because the associated"
							+ " course {} and faculty school id {} did not" + " resolve to an external course record.",
							new Object[] { earlyAlert.getId(), courseName, facultySchoolId });
				}
			}
		}
		Person creator = null;
		try {
			creator = personService.get(earlyAlert.getCreatedBy().getId());
		} catch (ObjectNotFoundException exp) {
			LOGGER.error("Early Alert Creator Not found sending message for early alert:" + earlyAlert.getId(), exp);
		}

		EarlyAlertMessageTemplateTO eaMTO = new EarlyAlertMessageTemplateTO(earlyAlert, creator);

		// Only early alerts response late messages sent to coaches
		if (eaMTO.getCoach() == null) {
			try {
				// if no earlyAlert.getCampus() error thrown by design, should never not be a
				// campus.
				eaMTO.setCoach(new CoachPersonLiteMessageTemplateTO(
						personService.get(earlyAlert.getCampus().getEarlyAlertCoordinatorId())));
			} catch (ObjectNotFoundException exp) {
				LOGGER.error("Early Alert with id: " + earlyAlert.getId()
						+ " does not have valid campus coordinator, no coach assigned: "
						+ earlyAlert.getCampus().getEarlyAlertCoordinatorId(), exp);
			}
		}

		String statusCode = eaMTO.getEnrollmentStatus();
		if (statusCode != null) {
			EnrollmentStatus enrollmentStatus = enrollmentStatusService.getByCode(statusCode);
			if (enrollmentStatus != null) {

				// if we have made it here... we can add the status!
				templateParameters.put("enrollment", enrollmentStatus);
			}
		}

		templateParameters.put("earlyAlert", eaMTO);
		templateParameters.put("termToRepresentEarlyAlert",
				configService.getByNameEmpty("term_to_represent_early_alert"));
		templateParameters.put("TermToRepresentEarlyAlert",
				configService.getByNameEmpty("term_to_represent_early_alert"));
		templateParameters.put("termForEarlyAlert", configService.getByNameEmpty("term_to_represent_early_alert"));
		templateParameters.put("linkToSSP", configService.getByNameEmpty("serverExternalPath"));
		templateParameters.put("applicationTitle", configService.getByNameEmpty("app_title"));
		templateParameters.put("institutionName", configService.getByNameEmpty("inst_name"));

		templateParameters.put("FirstName", eaMTO.getPerson().getFirstName());
		templateParameters.put("LastName", eaMTO.getPerson().getLastName());
		templateParameters.put("CourseName", eaMTO.getCourseName());

		return templateParameters;
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
	public void sendConfirmationMessageToFaculty(final EarlyAlert earlyAlert)
			throws ObjectNotFoundException, SendFailedException, ValidationException {
		if (earlyAlert == null) {
			throw new IllegalArgumentException("EarlyAlert was missing.");
		}

		if (earlyAlert.getPerson() == null) {
			throw new IllegalArgumentException("EarlyAlert.Person is missing.");
		}

		if (configService.getByNameOrDefaultValue("send_faculty_mail") != true) {
			LOGGER.debug("Skipping Faculty Early Alert Confirmation Email: Config Turned Off");
			return; // skip if faculty early alert email turned off
		}

		final UUID personId = earlyAlert.getCreatedBy().getId();
		Person person = personService.get(personId);
		if (person == null) {
			LOGGER.warn("EarlyAlert {} has no creator. Unable to send" + " confirmation message to faculty.",
					earlyAlert);
		} else {
			final SubjectAndBody subjAndBody = messageTemplateService
					.createEarlyAlertFacultyConfirmationMessage(fillTemplateParameters(earlyAlert));

			// Create and queue the message
			final Message message = messageService.createMessage(person, null, subjAndBody);

			LOGGER.info("Message {} created for EarlyAlert {}", message, earlyAlert);
		}
	}
}
