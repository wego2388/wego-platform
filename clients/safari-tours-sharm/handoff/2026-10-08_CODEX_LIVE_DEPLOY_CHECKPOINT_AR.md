# نقطة استكمال نشر سفاري — 8 أكتوبر 2026

> **تم تجاوز checkpoint02:18UTC أدناه بنشر فعلي ناجح.** الموقع والداشبورد
> يعملان بـHTTPS، حساب المالك دخل بنجاح، النسخ المشفرة واختبار الاستعادة
> خارج السيرفر والمراقبة فعّالة. المرجع الحالي الوحيد للاستكمال هو
> [تسليم الإنتاج](2026-10-08_PRODUCTION_DEPLOYED_HANDOFF_AR.md).
> لا تعِد first-install أو bootstrap من البنود التاريخية التالية.

## checkpoint سابق محفوظ للتاريخ — ليس حالة الإنتاج الحالية

تحديث02:18UTC: **لم يُشغَّل سفاري إنتاجيًا بعد**. OPS2-G وحده ACTIVE.
محمد أجاز النشر وتعديل البوابة اللازمة للمشروعين وcommit/push التغييرات
المحددة على الفروع الحالية فقط، دون main أو شغل كلودي الآخر.
Codex هو المنفذ الوحيد؛ لا تُشغّل نشرًا موازيًا.

## مثبت [x] / متبقٍ [ ]

- [x] فحوص سفاري الكاملة CI37714374585 على61b34cf SUCCESS، بما فيها
  Compose/Playwright الحقيقي. تطبيقات الإصدار من198959e، لا diff في
  platform/products/web مقارنة61b34cf. لا نسب CI القديم لإصلاحات جديدة.
- [x] صور التطبيق الثلاثة مبنية/اختُبرت: enquiryE2E24/24، site132/ERP329؛
  sharp/IPX WebP فعلي، no node-forge/braces directories في runtime.
  الاستثناءان HIGH معتمدان من محمد مع المتابعة، لا ادعاء wholeimageclean.
- [x] gateway fixture الحقيقي، guard7/verifier14، مراجعة مستقلة ACCEPT
  صفر blockers لعقد العزل/DNS/ROmounts/logprivacy.
- [x] public backup key فقط؛ private على جهاز محمد، encrypted synthetic
  backup+isolated restore PASS43tables/latestFlyway33. ليس نسخة إنتاجية.
- [x] ملفات وصور في private VPS staging فقط، checksums مطابقة. لا تغيير
  nginx أو الخدمات أو الشهادات أو قواعد بيانات السيرفر حتى هذه النقطة.
- [x] الخيمة4origins200/strictTLS0،9حاويات أصلية،83Gfree؛ baseline محفوظ.
- [x] تصحيح توقع الكتالوج من facts:30سجلًا/**27نشطة**، وليس29 القديمة.
  V25 المعتمد يغلق intro-diving وcrocodile-show؛ private-boat مغلق أيضًا.
  لا تغيير بيانات/إعادة تفعيل/تعديل migrations لهذا السبب.
- [x] فرع الخيمةb4317b79: عقد البوابة + source-onlyPyJWT2.15.0
  (9regressions+75authHTTP) + تصحيح fixturebranchcode4→18hex مع regression
  REDold/GREEN24 وreview مستقل. **صور تطبيق الخيمة الحالية لم تُستبدل**.
- [ ] CIالخيمة37716063910 علىb4317b79 كانIN_PROGRESS؛ تحقق الفعلية قبل
  rollout. السابق37714154337 FAILED بسبب fixture UNIQUE (4705pass/1fail)،
  لا يُحتسب نجاحًا. السابق37712408723 فشل auditPyJWT2.13 baseline.
- [ ] تجهيزSafariroot/privateenv، load+IDs، configguard، تشغيل5healthy.
- [ ] first-installSQL:22migrations/33/0failed،30/27catalog،32permissions،
  zero users/bookings/payments/slots/assets/E2E/foreignproducttables.
- [ ] root-owned mandatory Resortwrapper/guard/overlay/independentmarker؛
  tempnginx-t قبل nginx-onlyrecreate، تحققother8IDs/start/imagesunchanged.
- [ ] ACMEwebrootexisting/newSafari3SANlineage،liveatomicconfig،strictHTTPS
  Safari3names+Resort4،renewalSafari-onlydryrun/existingDockerreloadhook.
- [ ] firstadmin ownerinteractivepassword، productionencryptedbundle→local
  offsitepull→isolatedrestore→reportJSONback؛Safarionlybackup/healthtimers.
- [ ] actualbrowser/mobile/RTL/metadata/accessibility، finalhandoff+Board.

## المسارات والمصادر الدقيقة

- Safari `/home/wego/wego-safari-hardening`، `wego-016-safari-hardening`،
  infra`61b34cfbef7e2972b229251202de2d7a888cfc13`؛ app
  `198959efdd3754fffc9c357e555f25a014569b46`.
- Resort owningworktree `/home/wego/projects/resort-os-shared-gateway`،
  `codex/shared-gateway-20261008`، `b4317b796d98a3a0b6c72cb594a0f3abfe353524`.
  لا تعديل inv-adv/otheruserworktrees. liveResortsource
  `4983a7b0d53bfbcf905775b8278ee994900f1be3`.
- artifacts/evidence `/home/wego/safari-release-20261008.85V6vQ`؛
  VPSstage `/home/resortos/safari-stage-20261008.kpr3Me`0700.
- releaseplanned `/srv/safari-tours-sharm/releases/str-2026.10.08-61b34cf`؛
  sharedprivateenv0600، edge58080loopback، project`safari-tours-sharm-prod`.
- SSHexistingtrustedalias `resort-os-vps-next` userresortos to187.6.167.233؛
  StrictHostKeyCheckingyes،BatchModeyes،sudon. لا طباعةsecrets/env/config.
- authoritative runbook `../deployment/CONTAINER_GATEWAY_RUNBOOK_AR.md`،
  وليسhostNginx القديم. لا systemctlnginx ولاcertbotstandalone/nginxplugin.
- gatewaynetwork`safari-gateway`، onlyexistingnginx+Safariedgealias`safari-edge`.
- preparedexecutor `prepare-vps-release.sh` مستقل راجعهreviewer ACCEPT
  subjectbothCI/exactSHA/run+checksumrefresh. `PARTIAL_PREPARATION_RECOVERY.md`
  يحفظ خطوات استكمالpartial دونdeletesأعمى.
- artifacts `configure-vps-gateway.sh` وadmin/bootstrap/browser/SQL helpers
  ليست إثبات تنفيذ؛ طلبreviewأخير لم يكتمل بسببusagequota. استخدم العقد
  والقوالب/runbook التي راجعت سابقًا؛ لا تدّعreviewلم يحصل لهذهhelpers.

## سجلات مهمة وحدود

`runtime-images.tar.gz`sha256
`7d4666fb8ca466282ec4b8d9128f7891e868e7e07babbf75fe476fb8da91a5cf`.
bundlemanifest يحتوي3imageIDs/outputhashes/exactsource؛ يحتاجupdate
actualCI conclusions وnewResortSHA/run وإعادةحزم+checksum/narrowtransfer.
app198959 labelsصحيحة لأنapplicationtreesunchanged؛ لا تعيدبناءعشوائي.

النسخة المحلية `safari-release-verify`58108/PG55538 تحويE2Esynthetic فقط؛
لا تنقلقاعدتها/volumes/accounts. ownerprivatekey
`/home/wego/.local/share/safari-backup-keys-20261008`0700، fingerprint
`BFB5B27B3251F6921E9D37B62E0FD7716C91EB8C`؛NEVERVPS.
offsite `/home/wego/Backups/safari-tours-sharm`0700. approvedcertmail
`mohamedwagdy2323@gmail.com`،adminsame،Wagdioلاdisplaynamefieldحاليًا.
لاpassworddefault/chat/env، افتحownerTTYعندproductionhealthy.

المتبقي التجاري المسموح:ENQUIRY_ONLY،no mock/SMTP/analytics،images/content
uploadsعبرERP بعدالمالك،Paymobseparategate. receiverexternalalerts/weekly
automaticdrill/offlinekeyrecoverycopy notdone. ownerPCofflinesuspendscopies.
sourcePyJWTfixليسلiveResortpatch؛ separate applicationupgrade required.
لا blanketprune/down-v/mainmerge/DNSwrite/broadcleanup؛الحفاظعلىالخيمة
وشغلكلوديالآخرضروري. quota لا تبرر تجاوز gate أو ادعاءنشر.
