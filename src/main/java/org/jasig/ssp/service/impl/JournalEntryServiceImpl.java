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
package org.jasig.ssp.service.impl;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.lang.StringUtils;
import org.jasig.ssp.dao.JournalEntryDao;
import org.jasig.ssp.dao.PersonDao;
import org.jasig.ssp.model.JournalEntry;
import org.jasig.ssp.model.JournalEntryDetail;
import org.jasig.ssp.model.ObjectStatus;
import org.jasig.ssp.model.Person;
import org.jasig.ssp.service.AbstractRestrictedPersonAssocAuditableService;
import org.jasig.ssp.service.JournalEntryService;
import org.jasig.ssp.service.ObjectNotFoundException;
import org.jasig.ssp.service.PersonProgramStatusService;
import org.jasig.ssp.transferobject.reports.BaseStudentReportTO;
import org.jasig.ssp.transferobject.reports.EntityCountByCoachSearchForm;
import org.jasig.ssp.transferobject.reports.EntityStudentCountByCoachTO;
import org.jasig.ssp.transferobject.reports.JournalCaseNotesStudentReportTO;
import org.jasig.ssp.transferobject.reports.JournalStepSearchFormTO;
import org.jasig.ssp.transferobject.reports.JournalStepStudentReportTO;
import org.jasig.ssp.util.JournalCaseNotesStudentReportTOComparator;
import org.jasig.ssp.util.sort.PagingWrapper;
import org.jasig.ssp.util.sort.SortingAndPaging;
import org.jasig.ssp.web.api.validation.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class JournalEntryServiceImpl
		extends AbstractRestrictedPersonAssocAuditableService<JournalEntry>
		implements JournalEntryService {

	@Autowired
	private transient JournalEntryDao dao;

	@Autowired
	private transient PersonProgramStatusService personProgramStatusService;
	
	@Autowired
	private transient PersonDao personDao;

	@Override
	protected JournalEntryDao getDao() {
		return dao;
	}

	@Override
	public JournalEntry create(final JournalEntry obj)
			throws ObjectNotFoundException, ValidationException {
		final JournalEntry journalEntry = getDao().save(obj);
		checkForTransition(journalEntry);
		return journalEntry;
	}

	@Override
	public JournalEntry save(final JournalEntry obj)
			throws ObjectNotFoundException, ValidationException {
		final JournalEntry journalEntry = getDao().save(obj);
		checkForTransition(journalEntry);
		return journalEntry;
	}

	/**
	 * Search for a JournalStep that indicates a transition.
	 * 
	 * Collect the first item because no need to loop through others.
	 * 
	 * Then call transition if exists
	 * 
	 * @param journalEntry
	 * @throws ObjectNotFoundException
	 * @throws ValidationException
	 */
	private void checkForTransition(final JournalEntry journalEntry)
			throws ObjectNotFoundException, ValidationException {
		// search for a JournalStep that indicates a transition
		// findFirst because no need to loop through others
		Optional<JournalEntryDetail> journalEntryDetailForTransition = journalEntry.getJournalEntryDetails().stream()
				.filter(detail -> detail.getJournalStepJournalStepDetail().getJournalStep().isUsedForTransition())
				.findFirst();

		if (journalEntryDetailForTransition.isPresent()) {
			// is used for transition, so attempt to set program status
			personProgramStatusService
					.setTransitionForStudent(journalEntryDetailForTransition.get().getJournalEntry().getPerson());
		}
	}
	
	@Override
	public Long getCountForCoach(Person coach, Date createDateFrom, Date createDateTo, List<UUID> studentTypeIds){
		return dao.getJournalCountForCoach(coach, createDateFrom, createDateTo, studentTypeIds);
	}

	@Override
	public Long getStudentCountForCoach(Person coach, Date createDateFrom, Date createDateTo, List<UUID> studentTypeIds) {
		return dao.getStudentJournalCountForCoach(coach, createDateFrom, createDateTo, studentTypeIds);
	}
	
	@Override
	public PagingWrapper<EntityStudentCountByCoachTO> getStudentJournalCountForCoaches(EntityCountByCoachSearchForm form){
		return dao.getStudentJournalCountForCoaches(form);
	}
	
	@Override
	public PagingWrapper<JournalStepStudentReportTO> getJournalStepStudentReportTOsFromCriteria(JournalStepSearchFormTO personSearchForm,  
			SortingAndPaging sAndP){
		return dao.getJournalStepStudentReportTOsFromCriteria(personSearchForm,  
				sAndP);
	}

 	@Override
	public List<JournalCaseNotesStudentReportTO> getJournalCaseNoteStudentReportTOsFromCriteria(
			JournalStepSearchFormTO personSearchForm, SortingAndPaging sAndP) throws ObjectNotFoundException {
		final List<JournalCaseNotesStudentReportTO> personsWithJournalEntries = dao
				.getJournalCaseNoteStudentReportTOsFromCriteria(personSearchForm, sAndP);

		final Map<String, JournalCaseNotesStudentReportTO> mapJournalCaseNotesStudentReportBySchool = convertJournalCaseNotesStudentReportToMapBySchool(
				personsWithJournalEntries);

		if (personSearchForm.getJournalSourceIds() != null) {
			Optional<PagingWrapper<BaseStudentReportTO>> pwActiveStudentReportTO = Optional
					.ofNullable(personDao.getStudentReportTOs(personSearchForm,
							SortingAndPaging.createForSingleSortAll(ObjectStatus.ACTIVE, "lastName", "DESC")));

			if (pwActiveStudentReportTO.isPresent()) {
				convertActiveStudentReportToJournalCasesNotesStudentReportMapBySchoolAndFilterNewStudents(
						pwActiveStudentReportTO.get(), mapJournalCaseNotesStudentReportBySchool, personSearchForm,
						personsWithJournalEntries);
				sortByStudentName(personsWithJournalEntries);
			}
		}

		return personsWithJournalEntries;
	}

 	/**
 	 * Group JournalCaseNotes by School.
 	 * 
 	 * @param personsWithJournalEntries
 	 * @return
 	 */
	private Map<String, JournalCaseNotesStudentReportTO> convertJournalCaseNotesStudentReportToMapBySchool(
			List<JournalCaseNotesStudentReportTO> personsWithJournalEntries) {
		return personsWithJournalEntries.stream()
				.collect(Collectors.toMap(JournalCaseNotesStudentReportTO::getSchoolId, Function.identity()));
	}

	/**
	 * Filter new students that has coach from a list of active students journal
	 * notes and group by school.
	 * 
	 * @param pwActiveStudentReportTO
	 * @param mapJournalCaseNotesStudentReportBySchool
	 * @param personSearchForm
	 * @param personsWithJournalEntries
	 */
	private void convertActiveStudentReportToJournalCasesNotesStudentReportMapBySchoolAndFilterNewStudents(
			PagingWrapper<BaseStudentReportTO> pwActiveStudentReportTO,
			Map<String, JournalCaseNotesStudentReportTO> mapJournalCaseNotesStudentReportBySchool,
			JournalStepSearchFormTO personSearchForm, List<JournalCaseNotesStudentReportTO> personsWithJournalEntries) {

		Stream<BaseStudentReportTO> newStudentsWithCoach = filterNewStudentsAndHasCoach(pwActiveStudentReportTO,
				mapJournalCaseNotesStudentReportBySchool);

		for (BaseStudentReportTO studentReport : newStudentsWithCoach.collect(Collectors.toList())) {
			if (getDao().getJournalCountForPersonForJournalSourceIds(studentReport.getId(),
					personSearchForm.getJournalSourceIds()) == 0) {
				final JournalCaseNotesStudentReportTO entry = new JournalCaseNotesStudentReportTO(studentReport);
				personsWithJournalEntries.add(entry);
				mapJournalCaseNotesStudentReportBySchool.put(entry.getSchoolId(), entry);
			}
		}
	}

	/**
	 * Filter new students that has coach from a list of active students journal.
	 * 
	 * @param pwActiveStudentReportTO
	 * @param mapJournalCaseNotesStudentReportBySchool
	 * @return
	 */
	private Stream<BaseStudentReportTO> filterNewStudentsAndHasCoach(
			PagingWrapper<BaseStudentReportTO> pwActiveStudentReportTO,
			Map<String, JournalCaseNotesStudentReportTO> mapJournalCaseNotesStudentReportBySchool) {
		return StreamSupport.stream(pwActiveStudentReportTO.spliterator(), false).filter(
				studentReport -> !mapJournalCaseNotesStudentReportBySchool.containsKey(studentReport.getSchoolId())
						&& StringUtils.isNotBlank(studentReport.getCoachSchoolId()));
	}

	private static void sortByStudentName(List<JournalCaseNotesStudentReportTO> toSort) {
		Collections.sort(toSort, new JournalCaseNotesStudentReportTOComparator());
	}

}