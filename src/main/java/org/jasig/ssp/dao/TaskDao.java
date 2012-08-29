package org.jasig.ssp.dao;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.hibernate.Criteria;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.jasig.ssp.model.Person;
import org.jasig.ssp.model.Task;
import org.jasig.ssp.security.SspUser;
import org.jasig.ssp.util.sort.SortingAndPaging;
import org.springframework.stereotype.Repository;

/**
 * Task DAO
 */
@Repository
public class TaskDao
		extends AbstractRestrictedPersonAssocAuditableCrudDao<Task>
		implements RestrictedPersonAssocAuditableDao<Task> {

	protected TaskDao() {
		super(Task.class);
	}

	private void addCompleteRestriction(final boolean complete,
			final Criteria criteria) {

		if (complete) {
			criteria.add(Restrictions.isNotNull("completedDate"));
		} else {
			criteria.add(Restrictions.isNull("completedDate"));
		}
	}

	@SuppressWarnings(UNCHECKED)
	public List<Task> getAllForPersonId(final UUID personId,
			final boolean complete, final SspUser requestor,
			final SortingAndPaging sAndP) {

		final Criteria criteria = createCriteria(sAndP);
		criteria.add(Restrictions.eq("person.id", personId));

		addCompleteRestriction(complete, criteria);
		addConfidentialityLevelsRestriction(requestor, criteria);

		return criteria.list();
	}
   
	@SuppressWarnings(UNCHECKED)
	public List<Task> getAllForSessionId(final String sessionId,
			final SortingAndPaging sAndP) {

		final Criteria criteria = createCriteria(sAndP);
		criteria.add(Restrictions.eq("sessionId", sessionId));
		return criteria.list();
	}

	@SuppressWarnings(UNCHECKED)
	public List<Task> getAllForSessionId(final String sessionId,
			final boolean complete, final SortingAndPaging sAndP) {

		final Criteria criteria = createCriteria(sAndP);
		criteria.add(Restrictions.eq("sessionId", sessionId));

		addCompleteRestriction(complete, criteria);

		return criteria.list();
	}

	@SuppressWarnings(UNCHECKED)
	public List<Task> getAllWhichNeedRemindersSent(final SortingAndPaging sAndP) {

		final Criteria criteria = createCriteria(sAndP);
		criteria.add(Restrictions.isNull("completedDate"));
		criteria.add(Restrictions.isNull("reminderSentDate"));
		criteria.add(Restrictions.isNotNull("dueDate"));
		criteria.add(Restrictions.gt("dueDate", new Date()));
		return criteria.list();
	}

	@SuppressWarnings(UNCHECKED)
	public List<Task> getAllForPersonIdAndChallengeReferralId(
			final UUID personId, final boolean complete,
			final UUID challengeReferralId, final SspUser requestor,
			final SortingAndPaging sAndP) {

		final Criteria criteria = createCriteria(sAndP);
		criteria.add(Restrictions.eq("person.id", personId));
		criteria.add(Restrictions.eq("challengeReferral.id",
				challengeReferralId));

		addCompleteRestriction(complete, criteria);
		addConfidentialityLevelsRestriction(requestor, criteria);

		return criteria.list();
	}

	@SuppressWarnings(UNCHECKED)
	public List<Task> getAllForSessionIdAndChallengeReferralId(
			final String sessionId, final boolean complete,
			final UUID challengeReferralId, final SortingAndPaging sAndP) {

		final Criteria criteria = createCriteria(sAndP);
		criteria.add(Restrictions.eq("sessionId", sessionId));
		criteria.add(Restrictions.eq("challengeReferral.id",
				challengeReferralId));

		addCompleteRestriction(complete, criteria);

		return criteria.list();
	}


	public Long getTaskCountForCoach(Person coach, Date createDateFrom, Date createDateTo) {
		final Criteria query = createCriteria();

		// restrict to coach
		query.add(Restrictions.eq("createdBy", coach));
		
		if (createDateFrom != null) {
			query.add(Restrictions.ge("createdDate",
					createDateFrom));
		}

		if (createDateTo != null) {
			query.add(Restrictions.le("createdDate",
					createDateTo));
		}

		// item count
		Long totalRows = (Long) query.setProjection(Projections.rowCount())
				.uniqueResult();

		return totalRows;
	} 
	

	public Long getStudentTaskCountForCoach(Person coach, Date createDateFrom, Date createDateTo) {
		final Criteria query = createCriteria();

		if (createDateFrom != null) {
			query.add(Restrictions.ge("createdDate",
					createDateFrom));
		}

		if (createDateTo != null) {
			query.add(Restrictions.le("createdDate",
					createDateTo));
		}
		
		Long totalRows = (Long)query.add(Restrictions.eq("createdBy", coach))
		        .setProjection(Projections.countDistinct("person")).list().get(0);

		return totalRows;
	}	
	
	

	
}