# Adding a site calculator

Site calculators live in the **Site** tab. Each one is a single entry in the `SITE_CALCS` list in `web/index.html`
(search for `SITE_CALCS`). The tab, the input form, unit switching, saved values and the result rows are all automatic.

## One entry

```js
{id:'brick-wall', group:'Brickwork', name:'Bricks in a wall',
 about:'One line that says what it works out.',
 inputs:[
   {k:'L', label:'Length', type:'len', imp:'20', met:'6'},      // default for feet-inches and for metric
   {k:'H', label:'Height', type:'len', imp:'10', met:'3'},
   {k:'t', label:'Wall thickness', type:'select', options:['4½ in','9 in','13½ in'], def:'9 in'},
   {k:'waste', label:'Wastage', type:'pct', def:'5'},
   {k:'n', label:'Number of walls', type:'num', def:'1'}],
 calc:v=>[                                                    // v.L, v.H are already in metres
   {label:'Wall volume', v:v.L*v.H*0.2286, kind:'vol'},
   {label:'Bricks', v:500, kind:'count', unit:'bricks'}]}
```

## Input types
| type | the person types | `calc` receives |
|---|---|---|
| `len` | feet-inches Revit style (`12 6`, `0 4 1/2`) or metres | metres |
| `area` | ft² or m² | m² |
| `vol` | cft or m³ | m³ |
| `mass` | lb or kg | kg |
| `num` | any number or expression | the number |
| `pct` | percent | the number (5 means 5 %) |
| `select` | picks from `options` | the chosen text |

## Result kinds
`len` (shown as ft-in and m), `area` (ft² and m²), `vol` (cft and m³), `mass` (lb and kg),
`count` (rounded up, with `unit`), `num` (with optional `unit`), `text` (any words).
Every number row gets a **Use** button that sends it to the scientific calculator.

## Rules
- Work in SI inside `calc`; the screen converts to the unit system the person picked.
- Put the method's assumptions (brick size, mortar joint, dry-volume factor, mix ratio) as inputs with defaults,
  so the person can change them, and say them in `about`.
- Throw `Error('…')` with a plain sentence for impossible input; it is shown in place of the results.
