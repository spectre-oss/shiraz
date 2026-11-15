# SPDX-License-Identifier: GPL-3.0-only
# Report-only duplicate-code scan with PMD's CPD. Never fails the build.
.PHONY: lint-dup
CPD_BIN := .local/lint/pmd-bin-7.29.0/bin/pmd
CPD_MIN_TOKENS ?= 100
CPD_REPORT := build/lint/cpd.txt
lint-dup:
	@test -x $(CPD_BIN) || { echo 'Missing PMD; run make lint-tools'; exit 0; }
	@mkdir -p build/lint
	@: > $(CPD_REPORT)
	@for spec in java:java cpp:native,include python:src,scripts; do \
		lang=$${spec%%:*}; dirs=$${spec#*:}; set --; \
		for d in $$(echo $$dirs | tr , ' '); do [ -d $$d ] && set -- "$$@" --dir $$d; done; \
		[ $$# -gt 0 ] || continue; \
		echo "== $$lang (minimum tokens $(CPD_MIN_TOKENS))" >> $(CPD_REPORT); \
		$(CPD_BIN) cpd --minimum-tokens $(CPD_MIN_TOKENS) --language $$lang --format text \
			--skip-lexical-errors --no-fail-on-violation --no-fail-on-error "$$@" >> $(CPD_REPORT) 2>/dev/null || true; \
	done
	@echo "CPD report: $(CPD_REPORT) ($$(grep -c '^Found a' $(CPD_REPORT)) duplicate blocks)"
