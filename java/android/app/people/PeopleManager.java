// SPDX-License-Identifier: GPL-3.0-only
package android.app.people;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PeopleManager {
    private final Map<String, Map<String, ConversationStatus>> mStatuses =
            new LinkedHashMap<String, Map<String, ConversationStatus>>();

    public PeopleManager() {
    }

    public synchronized void addOrUpdateStatus(String conversationId, ConversationStatus status) {
        if (conversationId == null) throw new NullPointerException("conversationId");
        if (status == null) throw new NullPointerException("status");
        Map<String, ConversationStatus> perConv = mStatuses.get(conversationId);
        if (perConv == null) {
            perConv = new LinkedHashMap<String, ConversationStatus>();
            mStatuses.put(conversationId, perConv);
        }
        perConv.put(status.getId(), status);
    }

    public synchronized void clearStatus(String conversationId, String statusId) {
        if (conversationId == null) throw new NullPointerException("conversationId");
        if (statusId == null) throw new NullPointerException("statusId");
        Map<String, ConversationStatus> perConv = mStatuses.get(conversationId);
        if (perConv != null) {
            perConv.remove(statusId);
            if (perConv.isEmpty()) {
                mStatuses.remove(conversationId);
            }
        }
    }

    public synchronized void clearStatuses(String conversationId) {
        if (conversationId == null) throw new NullPointerException("conversationId");
        mStatuses.remove(conversationId);
    }

    public synchronized List<ConversationStatus> getStatuses(String conversationId) {
        if (conversationId == null) throw new NullPointerException("conversationId");
        Map<String, ConversationStatus> perConv = mStatuses.get(conversationId);
        if (perConv == null || perConv.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<ConversationStatus>(perConv.values());
    }
}
