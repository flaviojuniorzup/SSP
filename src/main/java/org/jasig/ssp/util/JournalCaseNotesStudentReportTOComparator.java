package org.jasig.ssp.util;

import java.util.Comparator;

import org.jasig.ssp.transferobject.reports.JournalCaseNotesStudentReportTO;

public class JournalCaseNotesStudentReportTOComparator implements Comparator<JournalCaseNotesStudentReportTO> {

	private static Comparator<String> nullSafeStringComparator = Comparator.nullsFirst(String::compareToIgnoreCase);

	private static Comparator<JournalCaseNotesStudentReportTO> metadataComparator = Comparator
			.comparing(JournalCaseNotesStudentReportTO::getLastName, nullSafeStringComparator)
			.thenComparing(JournalCaseNotesStudentReportTO::getFirstName, nullSafeStringComparator)
			.thenComparing(JournalCaseNotesStudentReportTO::getMiddleName, nullSafeStringComparator);

	@Override
	public int compare(JournalCaseNotesStudentReportTO o1, JournalCaseNotesStudentReportTO o2) {
		return metadataComparator.compare(o1, o2);
	}

}
