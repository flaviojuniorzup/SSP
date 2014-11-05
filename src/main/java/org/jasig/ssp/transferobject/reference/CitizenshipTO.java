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
package org.jasig.ssp.transferobject.reference;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.jasig.ssp.model.reference.Citizenship;
import org.jasig.ssp.transferobject.TransferObject;

import com.google.common.collect.Lists;

public class CitizenshipTO extends AbstractReferenceTO<Citizenship>
		implements TransferObject<Citizenship> {

	public CitizenshipTO() {
		super();
	}

	public CitizenshipTO(final UUID id, final String name,
			final String description) {
		super(id, name, description);
	}

	public CitizenshipTO(final Citizenship model) {
		super();
		from(model);
	}

	public static List<CitizenshipTO> toTOList(
			final Collection<Citizenship> models) {
		final List<CitizenshipTO> tObjects = Lists.newArrayList();
		for (Citizenship model : models) {
			tObjects.add(new CitizenshipTO(model));
		}
		return tObjects;
	}
}
