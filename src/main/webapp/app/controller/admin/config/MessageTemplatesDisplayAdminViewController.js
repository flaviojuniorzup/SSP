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
Ext.define('Ssp.controller.admin.config.MessageTemplatesDisplayAdminViewController', {
    extend: 'Deft.mvc.ViewController',
    mixins: [ 'Deft.mixin.Injectable' ],
    inject: {
    	apiProperties: 'apiProperties',
    	store: 'messageTemplatesStore',
    	unpagedStore: 'messageTemplatesStore',
    	formUtils: 'formRendererUtils',
		model: 'currentMessageTemplate',
		adminSelectedIndex: 'adminSelectedIndex',
		storeUtils:'storeUtils'
    },
    config: {
    	containerToLoadInto: 'adminforms',
    	formToDisplay: 'messagetemplatedetails'
    },
    control: {
    	'editButton': {
			click: 'onEditClick'
		},
        'previewButton': {
         	click: 'onPreviewClick'
         }
    },       
	init: function() {
		var me=this;
		
		var params = {store:me.store, 
				unpagedStore:me.unpagedStore, 
				propertyName:"name", 
				grid:me.getView(),
				model:me.model,
				selectedIndex: me.adminSelectedIndex};
		me.storeUtils.onStoreUpdate(params);
		
		return me.callParent(arguments);
    }, 
    
	onEditClick: function(button) {
		var grid, record, idx;
		grid = button.up('grid');
		record = grid.getView().getSelectionModel().getSelection()[0];

		this.adminSelectedIndex.set('value', -1);
        if (record) 
        {	
			this.model.data=record.data;
        	this.displayEditor();
        }else{
     	   Ext.Msg.alert('SSP Error', 'Please select an item to edit.'); 
        }
	},

	onPreviewClick: function(button) {
		var me = this;
   		var grid, record, idx;
   		grid = button.up('grid');
   		record = grid.getView().getSelectionModel().getSelection()[0];

   		this.adminSelectedIndex.set('value', -1);
        if (record)
        {
   			var id = record.data.id;

           	var url = me.apiProperties.createUrl(me.apiProperties.getItemUrl('messageTemplatePreview') + '?id=' + id);

			me.apiProperties.makeRequest({
				url: url,
				method: 'GET',
				successFunc: me.displayPreview,
				failureFunc: me.failureFunc,
				scope: me
			});

        }else{
            Ext.Msg.alert('SSP Error', 'Please select an item to preview.');
        }
   	},

   	failureFunc: function() {
	   	Ext.Msg.alert('SSP Error', 'Could not create message template preview.');
   	},

	destroy: function() {
		var me=this;
		if ( me.messageTemplatePopup ) {
			me.messageTemplatePopup.destroy();
		}
		return me.callParent( arguments );
	},

	displayEditor: function(){
		var comp = this.formUtils.loadDisplay(this.getContainerToLoadInto(), this.getFormToDisplay(), true, {});
	},

    displayPreview: function(response, scope){
		var me = scope;
    	if (response.responseText != "")
		{
		    var decoded = Ext.decode(response.responseText);
			if (decoded != null)
			{
				var model = new  Ext.create('Ssp.model.MessageTemplates');
				model.populateFromGenericObject(decoded);
			}
		}
		if ( me.messageTemplatePopup ) {
			me.messageTemplatePopup.destroy();
		}
		me.messageTemplatePopup = Ext.create('Ssp.view.admin.forms.config.MessageTemplatePreview', {
			messageTemplatePreviewData: model
		});
		me.messageTemplatePopup.show();
     }
});