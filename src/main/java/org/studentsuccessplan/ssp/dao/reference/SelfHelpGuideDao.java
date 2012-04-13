package org.studentsuccessplan.ssp.dao.reference;

import java.util.List;
import java.util.UUID;

import org.hibernate.Criteria;
import org.hibernate.FetchMode;
import org.hibernate.criterion.Restrictions;
import org.springframework.stereotype.Repository;
import org.studentsuccessplan.ssp.dao.AuditableCrudDao;
import org.studentsuccessplan.ssp.model.ObjectStatus;
import org.studentsuccessplan.ssp.model.reference.SelfHelpGuide;

/**
 * Data access class for the SelfHelpGuide reference entity.
 */
@Repository
public class SelfHelpGuideDao extends ReferenceAuditableCrudDao<SelfHelpGuide>
		implements AuditableCrudDao<SelfHelpGuide> {

	public SelfHelpGuideDao() {
		super(SelfHelpGuide.class);
	}

	public SelfHelpGuide getWithQuestions(UUID id) {
		Criteria query = sessionFactory.getCurrentSession()
				.createCriteria(SelfHelpGuide.class)
				.add(Restrictions.eq("id", id))
				.setFetchMode("selfHelpGuideQuestions", FetchMode.JOIN);

		return (SelfHelpGuide) query.uniqueResult();
	}

	@SuppressWarnings("unchecked")
	// :TODO paging
	public List<SelfHelpGuide> findAllActiveForUnauthenticated() {
		return sessionFactory
				.getCurrentSession()
				.createQuery(
						"from SelfHelpGuide " + "where objectStatus = ? "
								+ "and authenticationRequired = false "
								+ "order by name")
				.setParameter(0, ObjectStatus.ACTIVE).list();
	}

	@SuppressWarnings("unchecked")
	// :TODO paging
	public List<SelfHelpGuide> findAllActiveBySelfHelpGuideGroup(
			UUID selfHelpGuideGroupId) {
		return sessionFactory
				.getCurrentSession()
				.createQuery(
						"select shg " + "from SelfHelpGuide shg "
								+ "inner join shg.selfHelpGuideGroups shgg "
								+ "where shgg.id = ? "
								+ "and shg.objectStatus = ? "
								+ "order by shg.name")
				.setParameter(0, selfHelpGuideGroupId)
				.setParameter(1, ObjectStatus.ACTIVE).list();
	}

}
