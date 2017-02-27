/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2006-2014 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2014 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.web.svclayer;

import java.util.Collection;
import java.util.Map;

import org.opennms.netmgt.model.requisition.OnmsRequisition;

/**
 * This should not be used anymore and is deprecated.
 *
 * @deprecated Use {@link org.opennms.netmgt.provision.persist.ForeignSourceService} or {@link org.opennms.netmgt.provision.persist.RequisitionService} instead for now.
 */
@Deprecated
public interface ManualProvisioningService {
    
    Collection<String> getProvisioningGroupNames();
    
    OnmsRequisition getProvisioningGroup(String name);

    OnmsRequisition createProvisioningGroup(String name);

    Collection<String> getServiceTypeNames(String groupName);

    OnmsRequisition saveProvisioningGroup(String groupName, OnmsRequisition groupData);

    OnmsRequisition addNewNodeToGroup(String groupName, String nodeLabel);

    OnmsRequisition addCategoryToNode(String groupName, String pathToNode, String categoryName);

    OnmsRequisition addAssetFieldToNode(String groupName, String pathToNode, String fieldName, String fieldValue);

    OnmsRequisition addInterfaceToNode(String groupName, String pathToNode, String ipAddr);

    OnmsRequisition addServiceToInterface(String groupName, String pathToInterface, String serviceName);

    OnmsRequisition deletePath(String groupName, String pathToDelete);
    
    void importProvisioningGroup(String groupName);

    Collection<OnmsRequisition> getAllGroups();

    void deleteProvisioningGroup(String groupName);

    void deleteAllNodes(String groupName);

    Map<String, Integer> getGroupDbNodeCounts();

    Collection<String> getNodeCategoryNames();

    Collection<String> getAssetFieldNames();

}
