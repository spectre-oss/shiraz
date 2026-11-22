// SPDX-License-Identifier: GPL-3.0-only
#include "shiraz/platform.hpp"

#include <cstdint>

#if defined(_WIN32)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <locationapi.h>

extern "C" {

// Status codes match DarwinLocation:
//   0 = unavailable / no sensor
//   1 = ready / authorized
//   2 = access denied / disabled
//   3 = initializing
SHIRAZ_EXPORT int shiraz_win32_location_status() {
    const HRESULT coHr = CoInitializeEx(nullptr, COINIT_MULTITHREADED);
    const bool needUninit = SUCCEEDED(coHr);

    ILocation* loc = nullptr;
    int status = 0;
    if (SUCCEEDED(CoCreateInstance(CLSID_Location, nullptr, CLSCTX_INPROC_SERVER, IID_ILocation,
                                   reinterpret_cast<void**>(&loc))) &&
        loc) {
        LOCATION_REPORT_STATUS reportStatus = REPORT_NOT_SUPPORTED;
        if (SUCCEEDED(loc->GetReportStatus(IID_ILatLongReport, &reportStatus))) {
            switch (reportStatus) {
            case REPORT_RUNNING:
                status = 1;
                break;
            case REPORT_ACCESS_DENIED:
                status = 2;
                break;
            case REPORT_INITIALIZING:
                status = 3;
                break;
            default:
                status = 0;
                break;
            }
        }
        loc->Release();
    }

    if (needUninit)
        CoUninitialize();
    return status;
}

SHIRAZ_EXPORT int shiraz_win32_location_last_known(double* outLat, double* outLon,
                                                   double* outAccuracyM) {
    const HRESULT coHr = CoInitializeEx(nullptr, COINIT_MULTITHREADED);
    const bool needUninit = SUCCEEDED(coHr);

    ILocation* loc = nullptr;
    int ok = 0;
    if (SUCCEEDED(CoCreateInstance(CLSID_Location, nullptr, CLSCTX_INPROC_SERVER, IID_ILocation,
                                   reinterpret_cast<void**>(&loc))) &&
        loc) {
        ILocationReport* report = nullptr;
        if (SUCCEEDED(loc->GetReport(IID_ILatLongReport, &report)) && report) {
            ILatLongReport* ll = nullptr;
            if (SUCCEEDED(
                    report->QueryInterface(IID_ILatLongReport, reinterpret_cast<void**>(&ll))) &&
                ll) {
                DOUBLE lat = 0.0, lon = 0.0, err = 0.0;
                if (SUCCEEDED(ll->GetLatitude(&lat)) && SUCCEEDED(ll->GetLongitude(&lon))) {
                    if (outLat)
                        *outLat = lat;
                    if (outLon)
                        *outLon = lon;
                    if (outAccuracyM)
                        *outAccuracyM = SUCCEEDED(ll->GetErrorRadius(&err)) ? err : 0.0;
                    ok = 1;
                }
                ll->Release();
            }
            report->Release();
        }
        loc->Release();
    }

    if (needUninit)
        CoUninitialize();
    return ok;
}

} // extern "C"
#endif
