// SPDX-License-Identifier: GPL-3.0-only
#define _GNU_SOURCE 1
#include <time.h>
#include <locale.h>
#include <errno.h>
#include <stdint.h>
#include <string.h>
_Static_assert(sizeof(struct tm)==56 && _Alignof(struct tm)==8, "guest tm layout");
_Static_assert(__builtin_offsetof(struct tm,tm_gmtoff)==40 && __builtin_offsetof(struct tm,tm_zone)==48,"guest tm extensions");
#define CHECK(X) do {if(!(X))return __LINE__;} while(0)
int run_time(void) {
  time_t epoch=951782400;struct tm utc,local;char text[64];
  CHECK(gmtime_r(&epoch,&utc)==&utc && utc.tm_year==100 && utc.tm_mon==1 && utc.tm_mday==29 && utc.tm_wday==2);
  CHECK(utc.tm_hour==0 && utc.tm_min==0 && utc.tm_sec==0 && utc.tm_gmtoff==0 && utc.tm_zone);
  CHECK(strftime(text,sizeof(text),"%Y-%m-%d %H:%M:%S",&utc)==19 && !strcmp(text,"2000-02-29 00:00:00"));
  CHECK(timegm(&utc)==epoch && utc.tm_gmtoff==0);
  CHECK(localtime_r(&epoch,&local)==&local && mktime(&local)==epoch);
  char calendar[26];CHECK(ctime_r(&epoch,calendar)==calendar && strlen(calendar)==25 && calendar[24]=='\n');
  CHECK(!strcmp(ctime(&epoch),calendar));
  CHECK(gmtime(&epoch)->tm_mday==29 && localtime(&epoch));
  time_t now;CHECK(time(&now)==now && now>1000000000 && difftime(now,now-7)==7.0);
  locale_t locale=newlocale(LC_ALL_MASK,"C.UTF-8",0);CHECK(locale);
  CHECK(strftime_l(text,sizeof(text),"%Y-%m-%d",&utc,locale)==10 && !strcmp(text,"2000-02-29"));freelocale(locale);
  struct timespec zero={0,0},bad={0,1000000000},unchanged={7,8};
  CHECK(!nanosleep(&zero,0));errno=0;CHECK(nanosleep(&bad,&unchanged)==-1 && errno==EINVAL && unchanged.tv_sec==7 && unchanged.tv_nsec==8);
  return 0;
}
