# Key Transactions

## Component owners

- [Trask Stalnaker](https://github.com/trask), Microsoft

Learn more about component owners in [component_owners.yml](../.github/component_owners.yml).

### TEMPORARY NOTES

```
Sampler:
- if there is tracestate with transaction info:
  - populate transaction info from tracestate on span attributes
- if it's new transaction start based on attributes & kind (local root):
  - update tracestate 
    - transaction name = config(span)
	- start time = now()
  - add transaction.list|started
- if it's transaction end (based on attributes & kind):
  - maybe clear tracestate
  - update transaction.list|ended 
- if span does not participate in any transactions, fallback to delegated_sampler
- otherwise return RecordOnly (IsRecording=true, Sampled=delegated_sampler decision)

SpanProcessor:
- OnEnd
  - iterate over attributes[transaction.list]
	- report e2e latency for each with all the dimensions (based on attributes)

service (resource)
 
e2e latency (histogram)
  transaction.name =    -> from tracestate
  transaction.operation.name -> from span name 
  error.type -> from span error.type and/or status
  transaction.life_cycle (enum) ?

count is deriveable

Span attributes:
- transaction.list = [] // all transaction names that have started, consider comma-separated-list or just plain copy from tracestate, Order of magnitude: 10
 
- transaction.started = [] // all transactions that started on this span
- transaction.ended = [] // all transactions that ended on this span
 
How to clean up transactions in tracestate
Template attributes
 
transaction.{name}.start_time
transaction.start_time.{name}
```
