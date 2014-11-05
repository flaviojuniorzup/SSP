/*
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
Ext.define('Ssp.util.TemplateDataUtil',{
	extend: 'Ext.Component',

	initComponent: function() {
		return this.callParent( arguments );
    },
	
	prepareTemplateData: function( dataStore )
	{
		var arr = dataStore.data.items;
		var cleanArr = new Array();
		for (var i=0; i<arr.length; i++)
		{
			cleanArr.push( arr[i].raw );
		}

		return cleanArr;
	}
	
});