# Workforce Engine

Module: `workforce`. Phase 11.

## Aggregate model

Employees are simulated as **cohorts** (company, department, role, skill band, region) with count, average wage, morale, tenure. Individual employees exist only for named key roles (executives) where the game needs personalities. This keeps hundreds of thousands of workers affordable.

## Dynamics

- Hiring: openings, applicant flow from local labor supply, time to fill.
- Wages: contract wage vs. market wage affects retention and morale.
- Productivity = skill x morale factor x tooling.
- Training raises skill band gradually at a cost.
- Turnover driven by morale and wage gap; burnout from sustained overload.
- Labor shortages limit hiring and raise market wage.
- Automation reduces required labor and may displace cohorts with severance.

## Contracts and relations

Contracts set wage and term. Labor negotiations are events with outcomes drawn from the workforce stream influenced by morale and company reputation.

## Payroll

Payroll produces settlement intents each pay period; unpaid wages reduce morale sharply and create arrears.
