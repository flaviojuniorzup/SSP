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
package org.jasig.ssp.dao;

import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;
import org.jasig.ssp.model.Person;
import org.jasig.ssp.model.PersonEducationGoal;
import org.springframework.stereotype.Repository;

/**
 * CRUD methods for the PersonEducationGoal model.
 */
@Repository
public class PersonEducationGoalDao extends
		AbstractAuditableCrudDao<PersonEducationGoal> implements
		AuditableCrudDao<PersonEducationGoal> {

	/**
	 * Constructor
	 */
	public PersonEducationGoalDao() {
		super(PersonEducationGoal.class);
	}

	/**
	 * Return the education goal for the specified Person.
	 * 
	 * @param person
	 *            Lookup the education goal for this Person.
	 * 
	 * @return The education goal for the specified Person.
	 */
	public PersonEducationGoal forPerson(final Person person) {
		final Criteria query = sessionFactory.getCurrentSession()
				.createCriteria(PersonEducationGoal.class)
				.add(Restrictions.eq("person", person));
		return (PersonEducationGoal) query.uniqueResult();
	}
}