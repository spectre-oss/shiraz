// SPDX-License-Identifier: GPL-3.0-only
import data from './compatibility.json';
const report = 'https://github.com/spectre-oss/shiraz/blob/main/docs/';
export const applications = data.applications.map(app => ({...app, url: report + app.source}));
