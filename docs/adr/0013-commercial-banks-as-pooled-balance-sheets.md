# ADR-0013: Commercial banks as pooled balance sheets with a daily liquidity round and a monthly close

Status: accepted

## Context

The game has households, companies, a central bank that sets one policy rate, a ledger that records every payment and a
state that borrows. It has no commercial banks: companies borrow from the lending service at a rate derived from their
rating and deposits are not a thing. Players asked for banks to compete with and to run, and the economy needs the
channel that makes policy rates matter: deposits repricing at different speeds, credit tightening in a downturn,
losses eating capital, depositors losing faith, and a failure that spreads.

Three constraints shaped the model. A bank has to be cheap enough to simulate that a dozen of them cost no more than
one mid-sized company per tick. Everything must be deterministic and survive a save, so no per-loan objects with
identity, no hash-order iteration and no floating point. And the existing ledger and loan services must not be
rewritten: a bank must sit beside them and talk to them through the same settlement intents every other module uses.

## Decision

A bank is a set of pools, not a set of contracts. Loans are balances keyed by sector, original rating and stage
(performing, watch, impaired), each with a balance-weighted rate. Deposits are balances keyed by depositor type and
product, each with the rate paid. Securities are holdings by kind with a yield and duration. The rest of the balance
sheet is a handful of money fields (reserves, interbank positions, central bank borrowing, premises, capital
instruments, wholesale funding, retained earnings and the allowance). A bank's `imbalance` is assets less liabilities
and equity, and it is zero after every operation. Operations are written as moves between fields so the identity holds
by construction, and tests assert it after each of them.

Time is split in two. A **daily round** runs once per tick for each country: depositor confidence is updated, a run
turns into withdrawals, the interbank market clears at one overnight rate, a bank short of reserves is covered in a
fixed order (spare reserves, interbank, the central bank's marginal facility, the emergency facility, securities, a
fire sale of loans), and a bank that cannot be covered is handed to resolution. A **monthly close** runs every
30 ticks: interest, fees and costs are booked, floating loans reprice, loans migrate between stages and are written
off, the allowance is rebuilt from expected loss, tax is charged with a loss carry, new loans are originated in the
loan market, computer banks pay dividends each quarter, and the rating, the supervisory rung and the resolution test
are reviewed.

Deposit competition is share-based. Each bank quotes a rate for each product from the policy rate, a deposit beta
(how much of a policy move is passed on, small for current accounts and large for term money) and its own premium. The
month's new savings and the churn of existing balances are divided among banks by weights that grow with the premium
over the market, with a floor so nobody is shut out. Loan competition works the same way on price and standards; the
price of a loan is a cost-plus quote (funding cost, term and liquidity premiums, expected loss, capital charge,
operating cost) that the underwriter checks against the borrower's rating and the bank's sector limits.

Failure is a process, not an event. Capital moves a bank down a supervisory ladder with hysteresis: normal, inside the
buffer, below the requirement, severely and critically undercapitalised. Each rung restricts more (no dividends, no
growth, no new wholesale funding) and the last two trigger resolution. Confidence is a deterministic function of
capital, liquidity, recent losses, rating and system stress, that falls fast and recovers slowly, and gives four
stages (calm, watchful, run, panic). Resolution chooses the cheapest of recapitalisation with a bail-in, merger into a
stronger bank, or liquidation with payout of insured deposits from the guarantee fund, which is filled by a regular
levy, an extraordinary levy capped per year, and a loan from the state. Deposits that leave a troubled bank are
redistributed to the other banks of the country, so a run moves money and does not destroy it.

Computer banks follow a small strategy record (one of several archetypes) and a planner that adjusts the same policy
levers the player has, through the same `BankAction` and the same checks. The player's bank is never changed by a
planner. An advisor runs fixed rules over a bank's read model and the latest stress result, and suggests actions the
player can apply with one tap.

Nothing in the package posts to the ledger. Operations return `SettlementIntent`s and the caller settles them. Customer
deposits and the household and corporate side of loans are not mirrored in the ledger: they live inside the banking
state, and only the flows that touch real owners are expressed as intents (founders' capital, dividends, fees and
interest that a company or household actually pays or receives, tax, the cost of running the bank, guarantee fund levies
and payouts, central bank interest). The pipeline reads the economy through four narrow interfaces (`MonetaryView`,
`SovereignView`, `CreditDemandView`, `SavingsView`), so the package depends on `core`, `finance` and nothing else.

State is saved by `BankingCodec` (version byte, whole book, verification that the books balance and that references
point at known banks) and hashed by `BankingService.hashInto`. Events, advice and other derived views are not saved.

## Consequences

- The cost of a bank does not depend on how many loans it has made. Per-borrower detail (which company owes what) is
  not available; the loan market is a pool, and lending to a specific player company would need a separate path.
- The banking sector is self-consistent but loosely coupled to the rest of the economy until the adapter over the
  macro service is written. Until then `StaticBankingInputs` gives a calm economy and the sector only reacts to its own
  shocks.
- Because the sector is not mirrored in the ledger, the global money identity checked by the invariants is unaffected,
  and banking has its own invariant (every bank balances, interbank lent equals borrowed) tested in the package.
- Adding a lever means a `BankAction` variant, a case in `BankingActions`, a tag in `BankActionCodec`, a description in
  the presenters and a test for each. The tag numbers in the action codec never change meaning.
- The daily round and the monthly close are ordinary services behind `BankingService.step`, which makes them testable
  without a simulation; the price is that the pipeline must call it once per tick, after settlement for the previous
  tick and before the macro update, so the policy rate it reads is the one in force that day.
