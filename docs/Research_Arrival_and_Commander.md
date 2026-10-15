# Research: does "Arrival and BFP commander" fit Philippine volunteer firefighting?

Date: 2026-10-15
Question: the system now records an incident's **arrival time on scene**, the **BFP commander** the brigade reported to, and a log of the **orders** that commander gave (backend commit `58a2f77`, frontend commit `4d5f9d3`). Does this match how volunteer fire brigades in the Philippines actually work? If not, remove it.

**Conclusion: keep it.** The research supports the feature. Several sources were secondary or unofficial, so the confidence is moderate, not high. See "Limits of this research".

## Findings

### 1. Volunteers operate under a BFP commander at the scene
- The Fire Code's implementing rules (RA 9514, revised IRR, Section 6.0.4.5 "Command at Fire Scene") reportedly place fire brigades and fire volunteer organizations under the **direct operational control of BFP fire ground commanders** during firefighting. This was read in a reproduction of the 2019 revised IRR and a Scribd summary, not an official BFP copy.
- A Senate bill by Senator Sotto assumes the same arrangement: firefighting operations stay under the direct operational control of the BFP fire ground commander, while volunteers can be trained by outside parties. It is a bill, and its status was not confirmed.
- Meaning for the feature: "who is the BFP commander we report to" and "what did they assign us" are real parts of a volunteer brigade's work.

### 2. Arrival time is standard fire-reporting data
- The US NFIRS incident form records **alarm time** and **arrival time** together so response time can be measured. It is not a Philippine form, but it shows the field is conventional.
- The BFP Operational Procedures Manual lists an **After Fire Operation Report (AFOR)**, which stations file up the chain of command (Fire Marshal, then higher offices). Only the existence of the AFOR was seen, in an unofficial copy, not its fields.
- An older BFP investigation report template has "time fire started", "time of fire out" and "time and date of alarm". It has no arrival or ground-commander field. That template is for investigators, not for operations.

### 3. Accountability pressure on volunteer brigades
- After the 2023 Tondo crash involving a volunteer fire truck, the Interior Secretary ordered the BFP to adopt stricter guidelines for accrediting volunteer brigades. DILG Memorandum Circular 2008-090 sets accreditation procedures.
- A timed record of when volunteers arrived and what they were told to do supports that accountability.

## Terminology note
The BFP's own term is **Fire Ground Commander (FGC)**, not "Incident Commander". The UI, API and docs currently say "Incident Commander" or "BFP Incident Commander". A relabel of the UI text to "BFP Fire Ground Commander" is suggested. It was not made, and the API field name `commanderName` can stay as it is.

## Limits of this research
- The official BFP Operational Procedures Manual (bfp.gov.ph) returned HTTP 403, so its content was not read directly.
- No official BFP form with an "arrival" or "ground commander" field was found.
- The IRR provision was seen only in unofficial reproductions. The official BFP-published IRR should be checked.
- No source was found describing how Philippine volunteer brigades log their own operations.
- This is desk research. The best confirmation is someone from the brigade saying whether they note arrival time and the commander's instructions today.

## Sources
- [Fire Code notes (Scribd)](https://www.scribd.com/document/616682261/Fire-Code-Notes)
- [Sotto bill on BFP and the Fire Code (Inquirer)](https://newsinfo.inquirer.net/1090010/sotto-files-bill-to-make-bfp-sole-enforcer-of-countrys-fire-code/amp)
- [NFIRS incident form (USFA)](https://www.usfa.fema.gov/downloads/pdf/nfirs/nfirs-1.pdf)
- [BFP Operational Procedures Manual](https://bfp.gov.ph/wp-content/uploads/2015/09/BFP-Operational-Procedures-Manual.pdf)
- [Operational Procedures Manual copy (idoc.pub)](https://idoc.pub/documents/bfp-operational-procedures-manualpdf-en5k11vz2pno)
- [Volunteer fire truck crash coverage (Inquirer)](https://newsinfo.inquirer.net/1818600/when-rushing-firetrucks-end-up-causing-death-injuries)
