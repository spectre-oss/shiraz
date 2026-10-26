// SPDX-License-Identifier: GPL-3.0-only
public final class AutofillMetadataSmoke {public static void main(String[] args){
 final android.view.autofill.AutofillManager manager=android.view.autofill.AutofillManager.shirazUnavailable();
 android.content.Context context=new android.content.ContextWrapper(null){public Object getSystemService(String name){if(AUTOFILL_SERVICE.equals(name))return manager;return super.getSystemService(name);}};
 android.view.View first=new android.view.View(context),second=new android.view.View(context);first.setId(37);second.setId(37);
 android.view.autofill.AutofillId id=first.getAutofillId();if(id!=first.getAutofillId()||id.equals(second.getAutofillId())||id.isVirtual())throw new AssertionError("Stable distinct identity independent of resource ID");first.setId(99);if(first.getAutofillId()!=id)throw new AssertionError("Resource ID changes autofill identity");
 android.view.ViewTreeObserver observer=android.view.ViewTreeObserver.shirazCreateWindowObserver();first.shirazAttachTree(observer);first.shirazDetachTree();first.shirazAttachTree(observer);if(first.getAutofillId()!=id)throw new AssertionError("Attachment changes identity");first.shirazDetachTree();observer.shirazKill();
 android.view.autofill.AutofillId child=new android.view.autofill.AutofillId(id,12),same=new android.view.autofill.AutofillId(id.getViewId(),12);
 if(!child.isVirtual()||child.getVirtualChildId()!=12||!child.equals(same)||child.hashCode()!=same.hashCode()||child.equals(new android.view.autofill.AutofillId(id,13))||child.equals(id))throw new AssertionError("Virtual child identity");
 org.shiraz.fixture.AutofillMetadataFixture.check(context);if(context.getSystemService(android.view.autofill.AutofillManager.class)!=manager||manager.isEnabled()||manager.isAutofillSupported()||manager.hasEnabledAutofillServices())throw new AssertionError("Unavailable service capability");
}}
