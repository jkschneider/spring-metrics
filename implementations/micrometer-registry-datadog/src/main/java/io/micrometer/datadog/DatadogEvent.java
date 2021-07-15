/**
 * Copyright 2017 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.datadog;

import java.time.Instant;
import java.util.List;

public class DatadogEvent {
    String aggregationKey;
    String alertType;
    Instant dateHappened = Instant.now();
    List<String> deviceName;
    String host;
    String priority;
    Long relatedEventId;
    String sourceTypeName = "SYSTEM";
    List<String> tags;
    String title;
    String text;

    public DatadogEvent() {
    }

    public String getAggregationKey() {
        return this.aggregationKey;
    }

    public String getAlertType() {
        return this.alertType;
    }

    public Instant getDateHappened() {
        return this.dateHappened;
    }

    public List<String> getDeviceName() {
        return this.deviceName;
    }

    public String getHost() {
        return this.host;
    }

    public String getPriority() {
        return this.priority;
    }

    public Long getRelatedEventId() {
        return this.relatedEventId;
    }

    public String getSourceTypeName() {
        return this.sourceTypeName;
    }

    public List<String> getTags() {
        return this.tags;
    }

    public String getTitle() {
        return this.title;
    }

    public String getText() {
        return this.text;
    }

    public void setAggregationKey(String aggregationKey) {
        this.aggregationKey = aggregationKey;
    }

    public void setAlertType(String alertType) {
        this.alertType = alertType;
    }

    public void setDateHappened(Instant dateHappened) {
        this.dateHappened = dateHappened;
    }

    public void setDeviceName(List<String> deviceName) {
        this.deviceName = deviceName;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public void setRelatedEventId(Long relatedEventId) {
        this.relatedEventId = relatedEventId;
    }

    public void setSourceTypeName(String sourceTypeName) {
        this.sourceTypeName = sourceTypeName;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof DatadogEvent)) {
            return false;
        }
        final DatadogEvent other = (DatadogEvent)o;
        if (!other.canEqual((Object)this)) {
            return false;
        }
        final Object this$aggregationKey = this.getAggregationKey();
        final Object other$aggregationKey = other.getAggregationKey();
        if (this$aggregationKey == null
                ? other$aggregationKey != null
                : !this$aggregationKey.equals(other$aggregationKey)) {
            return false;
        }
        final Object this$alertType = this.getAlertType();
        final Object other$alertType = other.getAlertType();
        if (this$alertType == null
                ? other$alertType != null
                : !this$alertType.equals(other$alertType)) {
            return false;
        }
        final Object this$dateHappened = this.getDateHappened();
        final Object other$dateHappened = other.getDateHappened();
        if (this$dateHappened == null
                ? other$dateHappened != null
                : !this$dateHappened.equals(other$dateHappened)) {
            return false;
        }
        final Object this$deviceName = this.getDeviceName();
        final Object other$deviceName = other.getDeviceName();
        if (this$deviceName == null
                ? other$deviceName != null
                : !this$deviceName.equals(other$deviceName)) {
            return false;
        }
        final Object this$host = this.getHost();
        final Object other$host = other.getHost();
        if (this$host == null ? other$host != null : !this$host.equals(other$host)) {
            return false;
        }
        final Object this$priority = this.getPriority();
        final Object other$priority = other.getPriority();
        if (this$priority == null
                ? other$priority != null
                : !this$priority.equals(other$priority)) {
            return false;
        }
        final Object this$relatedEventId = this.getRelatedEventId();
        final Object other$relatedEventId = other.getRelatedEventId();
        if (this$relatedEventId == null
                ? other$relatedEventId != null
                : !this$relatedEventId.equals(other$relatedEventId)) {
            return false;
        }
        final Object this$sourceTypeName = this.getSourceTypeName();
        final Object other$sourceTypeName = other.getSourceTypeName();
        if (this$sourceTypeName == null
                ? other$sourceTypeName != null
                : !this$sourceTypeName.equals(other$sourceTypeName)) {
            return false;
        }
        final Object this$tags = this.getTags();
        final Object other$tags = other.getTags();
        if (this$tags == null ? other$tags != null : !this$tags.equals(other$tags)) {
            return false;
        }
        final Object this$title = this.getTitle();
        final Object other$title = other.getTitle();
        if (this$title == null ? other$title != null : !this$title.equals(other$title)) {
            return false;
        }
        final Object this$text = this.getText();
        final Object other$text = other.getText();
        if (this$text == null ? other$text != null : !this$text.equals(other$text)) {
            return false;
        }
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof DatadogEvent;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $aggregationKey = this.getAggregationKey();
        result = result * PRIME + ($aggregationKey == null ? 43 : $aggregationKey.hashCode());
        final Object $alertType = this.getAlertType();
        result = result * PRIME + ($alertType == null ? 43 : $alertType.hashCode());
        final Object $dateHappened = this.getDateHappened();
        result = result * PRIME + ($dateHappened == null ? 43 : $dateHappened.hashCode());
        final Object $deviceName = this.getDeviceName();
        result = result * PRIME + ($deviceName == null ? 43 : $deviceName.hashCode());
        final Object $host = this.getHost();
        result = result * PRIME + ($host == null ? 43 : $host.hashCode());
        final Object $priority = this.getPriority();
        result = result * PRIME + ($priority == null ? 43 : $priority.hashCode());
        final Object $relatedEventId = this.getRelatedEventId();
        result = result * PRIME + ($relatedEventId == null ? 43 : $relatedEventId.hashCode());
        final Object $sourceTypeName = this.getSourceTypeName();
        result = result * PRIME + ($sourceTypeName == null ? 43 : $sourceTypeName.hashCode());
        final Object $tags = this.getTags();
        result = result * PRIME + ($tags == null ? 43 : $tags.hashCode());
        final Object $title = this.getTitle();
        result = result * PRIME + ($title == null ? 43 : $title.hashCode());
        final Object $text = this.getText();
        result = result * PRIME + ($text == null ? 43 : $text.hashCode());
        return result;
    }

    public String toString() {
        return "DatadogEvent(aggregationKey=" +
                this.getAggregationKey() +
                ", alertType=" +
                this.getAlertType() +
                ", dateHappened=" +
                this.getDateHappened() +
                ", deviceName=" +
                this.getDeviceName() +
                ", host=" +
                this.getHost() +
                ", priority=" +
                this.getPriority() +
                ", relatedEventId=" +
                this.getRelatedEventId() +
                ", sourceTypeName=" +
                this.getSourceTypeName() +
                ", tags=" +
                this.getTags() +
                ", title=" +
                this.getTitle() +
                ", text=" +
                this.getText() +
                ")";
    }
}
