/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2017-2017 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2017 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.flows.classification.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.opennms.netmgt.flows.classification.ClassificationEngine;
import org.opennms.netmgt.flows.classification.ClassificationRequest;
import org.opennms.netmgt.flows.classification.ClassificationRuleProvider;
import org.opennms.netmgt.flows.classification.internal.classifier.Classifier;
import org.opennms.netmgt.flows.classification.internal.classifier.CombinedClassifier;
import org.opennms.netmgt.flows.classification.internal.matcher.IpMatcher;
import org.opennms.netmgt.flows.classification.internal.matcher.Matcher;
import org.opennms.netmgt.flows.classification.internal.matcher.PortMatcher;
import org.opennms.netmgt.flows.classification.internal.matcher.ProtocolMatcher;
import org.opennms.netmgt.flows.classification.internal.provider.StaticClassificationRuleProvider;
import org.opennms.netmgt.flows.classification.persistence.api.Rule;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

public class DefaultClassificationEngine implements ClassificationEngine {

    private final ClassificationRuleProvider ruleProvider;
    private final List<Classifier> classifierList = new ArrayList<>();
    private final boolean useStaticRules;

    // This can be a DAO or similar in the future
    public DefaultClassificationEngine(ClassificationRuleProvider ruleProvider) {
        this(ruleProvider, false);
    }

    public DefaultClassificationEngine(ClassificationRuleProvider ruleProvider, boolean useStaticRules) {
        this.ruleProvider = Objects.requireNonNull(ruleProvider);
        this.useStaticRules = useStaticRules;
        this.reload();
    }

    @Override
    public void reload() {
        classifierList.clear();
        ruleProvider.getRules().forEach(rule -> {
            final Classifier classifier = createClassifier(rule);
            if (!classifierList.contains(classifier)) {
                classifierList.add(classifier);
            }
        });
        Collections.sort(classifierList, new ClassificationSorter());

        // For now we just apply static rules at the end.
        // This ensures that user-defined rules are loaded first
        if (useStaticRules) {
            try {
                new StaticClassificationRuleProvider().getRules()
                    .forEach(rule -> {
                        final Classifier classifier = createClassifier(rule);
                        if (!classifierList.contains(classifier)) {
                            classifierList.add(classifier);
                        }
                    });
            } catch (IOException e) {
                LoggerFactory.getLogger(getClass()).error("Could not load static rules {}", e.getMessage(), e);
            }
        }
    }

    @Override
    public String classify(ClassificationRequest classificationRequest) {
        final Optional<String> first = classifierList.stream()
                .map(classifier -> classifier.classify(classificationRequest))
                .filter(classifier -> classifier != null)
                .findFirst();

        // We return null instead of 'Undefined', to let the caller (e.g. rest service, or ui) decide
        // what an unmapped definition should be named.
        // This prevents a collision with an existing rule, which may map to 'Undefined'
        return first.orElse(null);
    }

    private static Classifier createClassifier(Rule rule) {
        final List<Matcher> matchers = new ArrayList<>();
        if (!Strings.isNullOrEmpty(rule.getProtocol())) {
            matchers.add(new ProtocolMatcher(rule.getProtocol()));
        }
        if (!Strings.isNullOrEmpty(rule.getIpAddress())) {
            matchers.add(new IpMatcher(rule.getIpAddress()));
        }
        if (!Strings.isNullOrEmpty(rule.getPort())) {
            matchers.add(new PortMatcher(rule.getPort()));
        }
        return new CombinedClassifier(rule.getName(), matchers);
    }

}
