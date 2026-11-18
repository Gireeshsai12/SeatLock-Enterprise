import React, {Component, useEffect, useState} from 'react';
import {createRoot} from 'react-dom/client';
import {api, categories, date, dollars, message} from './api';
import type {Cart, Listing, Order, Seat, Session, Venue} from './api';
import './style.css';

type View = 'events'|'orders'|'admin';
type Options = {demo:boolean;google:boolean};
const levels = {CONCERT:'Concert',MOVIE:'Movie',SPORTS:'Sports',CONFERENCE:'Conference',PERFORMANCE:'Performance'};
const seatLabel = (seat:Seat) => String.fromCharCode(64 + seat.row_number) + seat.seat_number;
const initialSession = ():Session|undefined => {
  const saved = sessionStorage.getItem('seatlock.session');
  try {return saved ? JSON.parse(saved) : undefined;} catch {return undefined;}
};

class Boundary extends Component<{children:React.ReactNode},{failed:boolean}> {
  state={failed:false};
  static getDerivedStateFromError(){return {failed:true};}
  render(){return this.state.failed ? <main><h1>SeatLock couldn’t load</h1><button onClick={()=>location.reload()}>Reload page</button></main> : this.props.children;}
}

function App(){
  const [session,setSession] = useState(initialSession);
  const [options,setOptions] = useState<Options>();
  const [view,setView] = useState<View>('events');
  const [auth,setAuth] = useState<'login'|'register'>();
  const [event,setEvent] = useState<Listing>();
  const [notice,setNotice] = useState('');
  useEffect(()=>{
    api<Options>('/auth/options').then(setOptions).catch(error=>setNotice(message(error)));
    const hash = new URLSearchParams(location.hash.slice(1));
    const token = hash.get('access_token');
    if (token) {
      history.replaceState(null,'',location.pathname);
      signIn({accessToken:token,email:'Google account',role:'CUSTOMER'});
    } else if (location.hash === '#oauth-error') {
      history.replaceState(null,'',location.pathname);
      setNotice('Google sign-in could not be completed.');
    }
  },[]);
  function signIn(user:Session){
    sessionStorage.setItem('seatlock.session',JSON.stringify(user));
    setSession(user);
    setAuth(undefined);
  }
  function signOut(){
    sessionStorage.removeItem('seatlock.session');
    setSession(undefined);
    setView('events');
    setEvent(undefined);
  }
  function navigate(next:View){
    setView(next); setEvent(undefined); setAuth(undefined); setNotice('');
  }
  return <>
    <header><div className="shell nav"><button className="brand" onClick={()=>navigate('events')}><span className="mark">S</span><span>SeatLock<small>ENTERPRISE</small></span></button>
      <nav aria-label="Main navigation">
        <button aria-current={view==='events'?'page':undefined} onClick={()=>navigate('events')}>Discover</button>
        {session && <button aria-current={view==='orders'?'page':undefined} onClick={()=>navigate('orders')}>My reservations</button>}
        {session?.role==='ADMIN' && <button aria-current={view==='admin'?'page':undefined} onClick={()=>navigate('admin')}>Operations</button>}
      </nav>
      <div className="account">{session ? <><span>{session.email}</span><button className="secondary" onClick={signOut}>Sign out</button></> : <button className="secondary" onClick={()=>setAuth('login')}>Sign in</button>}</div>
    </div></header>
    <main className="shell">
      {options?.demo && <p className="demo"><i></i> LOCAL DEMO <span>Payments, refunds and email are simulated by default.</span></p>}
      {notice && <p role="alert" className="error">{notice}</p>}
      {auth && <Auth mode={auth} switchMode={setAuth} close={()=>setAuth(undefined)} options={options} success={signIn}/>}
      {view==='events' && (event ? <Booking key={event.id} item={event} session={session} signIn={()=>setAuth('login')} back={()=>setEvent(undefined)}/> : <Catalog pick={setEvent}/>)}
      {view==='orders' && session && <Orders session={session}/>}
      {view==='admin' && session?.role==='ADMIN' && <Admin session={session}/>}
    </main>
    <footer><div className="shell">SeatLock Enterprise <a href="/swagger-ui/index.html">API reference</a></div></footer>
  </>;
}

function Auth({mode,switchMode,close,options,success}:{mode:'login'|'register';switchMode:(mode:'login'|'register')=>void;close:()=>void;options?:Options;success:(user:Session)=>void}){
  const [error,setError]=useState('');
  const [busy,setBusy]=useState(false);
  const registering=mode==='register';
  async function submit(event:React.FormEvent<HTMLFormElement>){
    event.preventDefault(); setError(''); setBusy(true);
    const values=Object.fromEntries(new FormData(event.currentTarget));
    try {success(await api<Session>('/auth/'+mode,undefined,'POST',values));}
    catch(error){setError(message(error));}
    finally {setBusy(false);}
  }
  return <section className="panel auth" aria-labelledby="auth-heading">
    <div className="spread"><h2 id="auth-heading">{registering?'Create account':'Sign in'}</h2><button className="text-button" onClick={close}>Close</button></div>
    <form onSubmit={submit}>
      <label>Email<input name="email" autoComplete="email" type="email" required maxLength={254}/></label>
      <label>Password<input name="password" autoComplete={registering?'new-password':'current-password'} type="password" required minLength={10} maxLength={72}/></label>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="actions"><button disabled={busy}>{registering?'Create account':'Sign in'}</button>
        <button className="secondary" type="button" onClick={()=>{setError('');switchMode(registering?'login':'register');}}>{registering?'Use existing account':'Create an account'}</button>
        {options?.google && <a href="/oauth2/authorization/google">Continue with Google</a>}
      </div>
    </form>
  </section>;
}

function Catalog({pick}:{pick:(item:Listing)=>void}){
  const [items,setItems]=useState<Listing[]>();
  const [error,setError]=useState('');
  const [query,setQuery]=useState('');
  const [category,setCategory]=useState('');
  const [sort,setSort]=useState('upcoming');
  const [page,setPage]=useState(0);
  const size=12;
  useEffect(()=>{
    let active=true;
    setItems(undefined); setError('');
    const timer=setTimeout(()=>{
      const params=new URLSearchParams({q:query,sort,page:String(page),size:String(size)});
      if(category)params.set('category',category);
      api<Listing[]>('/events?'+params).then(rows=>{if(active)setItems(rows);})
        .catch(error=>{if(active)setError(message(error));});
    },140);
    return ()=>{active=false;clearTimeout(timer);};
  },[query,category,sort,page]);
  return <>
    <section className="hero"><div><p className="eyebrow">THE EVENT DIRECTORY</p><h1>Find your next<br/>night out.</h1><p>Explore upcoming events and choose your seat.</p></div><div className="ticket" aria-hidden="true"><span>SEATLOCK</span><b>ADMIT<br/>ONE</b><div className="barcode"></div></div></section>
    <div className="spread heading"><h2>Upcoming events</h2></div>
    <div className="filters">
      <label>Search events<input type="search" value={query} maxLength={120} placeholder="Event name or city" onChange={e=>{setQuery(e.target.value);setPage(0);}}/></label>
      <label>Category<select value={category} onChange={e=>{setCategory(e.target.value);setPage(0);}}><option value="">All categories</option>{categories.map(c=><option key={c} value={c}>{levels[c]}</option>)}</select></label>
      <label>Sort by<select value={sort} onChange={e=>{setSort(e.target.value);setPage(0);}}><option value="upcoming">Event date</option><option value="price">Lowest price</option><option value="title">Event name</option></select></label>
    </div>
    {error && <p role="alert" className="error">{error}</p>}
    {!items && !error && <p role="status">Loading events…</p>}
    {items?.length===0 && <p className="panel">No matching events on this page.</p>}
    <div className="events">{items?.map(item=><article className="event-card" key={item.id}>
      <div className={'event-art '+item.category.toLowerCase()}><span>{levels[item.category]}</span><b aria-hidden="true">{({CONCERT:'♫',MOVIE:'▣',SPORTS:'◉',CONFERENCE:'✳',PERFORMANCE:'✦'})[item.category]}</b></div>
      <div className="card-body"><small>{date(item.starts_at)}</small><h3>{item.title}</h3><p>{item.venue} · {item.city}</p><div className="spread"><strong>{dollars(item.price_cents)}</strong><button onClick={()=>pick(item)}>Choose seats</button></div></div>
    </article>)}</div>
    {(page>0 || items?.length===size) && <div className="actions"><button className="secondary" disabled={page===0} onClick={()=>setPage(page-1)}>Previous</button><span>Page {page+1}</span><button className="secondary" disabled={items?.length!==size} onClick={()=>setPage(page+1)}>Next</button></div>}
  </>;
}

function Booking({item,session,signIn,back}:{item:Listing;session?:Session;signIn:()=>void;back:()=>void}){
  const [seats,setSeats]=useState<Seat[]>();
  const [picked,setPicked]=useState<Seat[]>([]);
  const [cart,setCart]=useState<Cart>();
  const [error,setError]=useState('');
  const [inventoryError,setInventoryError]=useState('');
  const [busy,setBusy]=useState(false);
  const [now,setNow]=useState(Date.now());
  useEffect(()=>{
    let active=true;
    async function refresh(){
      try {
        const rows=await api<Seat[]>(`/events/${item.id}/seats`);
        if(active){setSeats(rows);setInventoryError('');}
      } catch(error){if(active)setInventoryError(message(error));}
    }
    void refresh();
    const timer=setInterval(()=>{void refresh();setNow(Date.now());},2000);
    return ()=>{active=false;clearInterval(timer);};
  },[item.id]);
  const remaining=cart ? Math.max(0,Math.ceil((Date.parse(cart.expiresAt)-now)/1000)) : 0;
  const pending=cart?.status==='HELD';
  function toggle(seat:Seat){
    setPicked(old=>old.some(current=>seatLabel(current)===seatLabel(seat))
      ? old.filter(current=>seatLabel(current)!==seatLabel(seat))
      : old.length < 8 ? [...old,seat] : old);
  }
  async function action(path:string,body?:unknown){
    if(!session){signIn();return;}
    setBusy(true);setError('');
    try{
      const result=await api<Cart>(path,session,'POST',body);
      setCart(result);
      setNow(Date.now());
      if(result.status!=='HELD')setPicked([]);
      setSeats(await api<Seat[]>(`/events/${item.id}/seats`));
    }catch(error){setError(message(error));}
    finally{setBusy(false);}
  }
  const rows=Array.from(new Set(seats?.map(seat=>seat.row_number) ?? []));
  return <section>
    <button className="text-button" onClick={back}>← Back to events</button>
    <div className="heading"><p className="eyebrow">{levels[item.category]} · {date(item.starts_at)}</p><h1>{item.title}</h1><p>{item.venue} · {item.city}</p></div>
    <div className="booking">
      <section className="panel"><h2>Seat map</h2><p className="muted">Availability refreshes every two seconds.</p><div className="stage">STAGE / SCREEN</div>
        {inventoryError && <p role="alert" className="error">{inventoryError}</p>}
        {!seats && !inventoryError && <p role="status">Loading seats…</p>}
        <div className="seat-map">{rows.map(row=><div className="seat-row" key={row}>{seats?.filter(seat=>seat.row_number===row).map(seat=>{
          const chosen=picked.some(value=>seatLabel(value)===seatLabel(seat));
          return <button className="seat" key={seatLabel(seat)} aria-label={`Seat ${seatLabel(seat)}, ${seat.state.toLowerCase()}`}
            aria-pressed={chosen} data-state={seat.state} disabled={busy || pending || (seat.state!=='AVAILABLE' && !chosen)} onClick={()=>toggle(seat)}>{seatLabel(seat)}</button>;
        })}</div>)}</div>
        <p className="muted">Green = available · Gold = selected · Gray = held or booked</p>
      </section>
      <aside className="panel cart"><h2>Your cart</h2><p>{picked.length ? picked.map(seatLabel).join(', ') : 'Select up to eight seats.'}</p>
        <p className="amount">{dollars(pending && cart ? cart.totalCents : picked.length*item.price_cents)}</p>
        {error && <p role="alert" className="error">{error}</p>}
        {cart?.status==='CONFIRMED' && <p role="status" className="success">Reservation confirmed. See My reservations for details.</p>}
        {cart?.status==='PAYMENT_FAILED' && <p role="status" className="error">Demo payment declined. The seats have been released.</p>}
        {cart?.status==='CANCELLED' && <p role="status">Hold released.</p>}
        {pending && cart ? <>
          <p>{remaining ? `Hold expires in ${remaining} seconds.` : 'Your hold expired.'}</p>
          <div className="stack">
            {remaining>0 && <><button disabled={busy} onClick={()=>void action('/reservations/'+cart.id+'/checkout',{scenario:'APPROVE'})}>Confirm demo payment</button>
              <button className="secondary" disabled={busy} onClick={()=>void action('/reservations/'+cart.id+'/checkout',{scenario:'DECLINE'})}>Simulate declined payment</button></>}
            <button className="secondary" disabled={busy} onClick={()=>void action('/reservations/'+cart.id+'/cancel')}>Release hold</button>
          </div>
        </> : <button disabled={!picked.length || busy} onClick={()=>void action('/reservations',{eventId:item.id,seats:picked.map(seat=>({row:seat.row_number,number:seat.seat_number}))})}>{session?'Hold selected seats':'Sign in to hold seats'}</button>}
      </aside>
    </div>
  </section>;
}

function Orders({session}:{session:Session}){
  const [orders,setOrders]=useState<Order[]>();
  const [page,setPage]=useState(0);
  const [error,setError]=useState('');
  const [busy,setBusy]=useState('');
  const [revision,setRevision]=useState(0);
  useEffect(()=>{
    let active=true;setOrders(undefined);
    api<Order[]>('/reservations?page='+page,session).then(rows=>{if(active)setOrders(rows);})
      .catch(error=>{if(active)setError(message(error));});
    return ()=>{active=false;};
  },[session,page,revision]);
  async function cancel(id:string){
    setBusy(id);setError('');
    try {await api('/reservations/'+id+'/cancel',session,'POST');setRevision(r=>r+1);}
    catch(error){setError(message(error));}
    finally{setBusy('');}
  }
  return <section><h1>My reservations</h1>{error && <p role="alert" className="error">{error}</p>}
    {!orders && !error && <p role="status">Loading reservations…</p>}
    {orders && <div className="panel table-wrap">{orders.length===0 ? <p>No reservations on this page.</p> :
      <table><thead><tr><th>Event</th><th>Seats</th><th>Total</th><th>Status</th><th>Payment</th><th>Action</th></tr></thead>
      <tbody>{orders.map(order=><tr key={order.id}><td>{order.title}</td><td>{order.seats}</td><td>{dollars(order.total_cents)}</td><td>{order.status}</td><td>{order.payment_status ?? '—'}</td>
        <td>{(order.status==='CONFIRMED'||order.status==='HELD') && <button className="secondary" disabled={!!busy} onClick={()=>void cancel(order.id)}>{order.status==='CONFIRMED'?'Cancel and simulate refund':'Release hold'}</button>}</td></tr>)}</tbody></table>}</div>}
    <div className="actions"><button className="secondary" disabled={page===0} onClick={()=>setPage(page-1)}>Previous</button><button className="secondary" disabled={orders?.length!==20} onClick={()=>setPage(page+1)}>Next</button></div>
  </section>;
}

type Audit = {id:number;action:string;entity:string;created_at:string};
type Notice = {id:string;subject:string;delivery_mode:string;delivered_at:string};
function Admin({session}:{session:Session}){
  const [metrics,setMetrics]=useState<Record<string,number>>();
  const [audit,setAudit]=useState<Audit[]>([]);
  const [mail,setMail]=useState<Notice[]>([]);
  const [venues,setVenues]=useState<Venue[]>([]);
  const [events,setEvents]=useState<Listing[]>([]);
  const [editVenue,setEditVenue]=useState<Venue>();
  const [editEvent,setEditEvent]=useState<Listing>();
  const [status,setStatus]=useState('');
  const [error,setError]=useState('');
  const [busy,setBusy]=useState(false);
  async function refresh(){
    try{
      const [m,a,n,v,e]=await Promise.all([
        api<Record<string,number>>('/admin/analytics',session),api<Audit[]>('/admin/audit',session),
        api<Notice[]>('/admin/notifications',session),api<Venue[]>('/admin/venues',session),api<Listing[]>('/events?size=100',session)]);
      setMetrics(m);setAudit(a);setMail(n);setVenues(v);setEvents(e);
    }catch(error){setError(message(error));}
  }
  useEffect(()=>{void refresh();},[session]);
  async function submit(event:React.FormEvent<HTMLFormElement>,kind:'venues'|'events'){
    event.preventDefault();setError('');setStatus('');setBusy(true);
    const form=event.currentTarget;
    const values=new FormData(form);
    const text=(key:string)=>String(values.get(key));
    const number=(key:string)=>Number(values.get(key));
    const body=kind==='venues' ? {name:text('name'),city:text('city'),rows:number('rows'),seatsPerRow:number('seatsPerRow')} : {
      title:text('title'),category:text('category'),venueId:editEvent?.venue_id ?? number('venueId'),priceCents:number('priceCents'),
      startsAt:new Date(text('startsAt')).toISOString(),version:editEvent?.version
    };
    const id=kind==='venues'?editVenue?.id:editEvent?.id;
    try{
      await api('/admin/'+kind+(id?'/'+id:''),session,id?'PUT':'POST',body);
      setStatus('Changes saved.');
      setEditVenue(undefined);setEditEvent(undefined);
      form.reset();
      await refresh();
    }catch(error){setError(message(error));}
    finally{setBusy(false);}
  }
  async function reconcile(){
    setError('');setBusy(true);
    try{const result=await api<{released:number}>('/admin/inventory/reconcile',session,'POST');setStatus(`Released ${result.released} expired seat assignments.`);await refresh();}
    catch(error){setError(message(error));}
    finally{setBusy(false);}
  }
  const localTime=(value:string)=>{
    const timestamp=new Date(value);
    return new Date(timestamp.getTime()-timestamp.getTimezoneOffset()*60000).toISOString().slice(0,16);
  };
  return <section><div className="spread heading"><h1>Operations</h1><div className="actions"><button className="secondary" onClick={()=>void refresh()}>Refresh</button><button disabled={busy} onClick={()=>void reconcile()}>Reconcile inventory</button></div></div>
    {status && <p role="status" className="success">{status}</p>}{error && <p role="alert" className="error">{error}</p>}
    <div className="metrics">{metrics ? Object.entries(metrics).map(([key,value])=><div key={key} className="panel"><small>{key.replaceAll('_',' ')}</small><strong>{key==='net_cents'?dollars(value):value}</strong></div>) : <p role="status">Loading analytics…</p>}</div>
    <div className="admin-forms">
      <section className="panel"><h2>Venues</h2><label>Edit layout<select value={editVenue?.id ?? ''} onChange={e=>setEditVenue(venues.find(venue=>venue.id===+e.target.value))}><option value="">New venue</option>{venues.map(venue=><option key={venue.id} value={venue.id}>{venue.name}</option>)}</select></label>
        <form key={editVenue?.id ?? 'new'} onSubmit={e=>void submit(e,'venues')}>
          <label>Venue name<input name="name" defaultValue={editVenue?.name} required maxLength={120}/></label><label>City<input name="city" defaultValue={editVenue?.city} required maxLength={120}/></label>
          <label>Rows<input name="rows" defaultValue={editVenue?.rows_count ?? 5} type="number" min={1} max={26} required/></label><label>Seats per row<input name="seatsPerRow" defaultValue={editVenue?.seats_per_row ?? 8} type="number" min={1} max={30} required/></label>
          <button disabled={busy}>{editVenue?'Save venue':'Create venue'}</button>
        </form>
      </section>
      <section className="panel"><h2>Events</h2><label>Edit event<select value={editEvent?.id ?? ''} onChange={e=>setEditEvent(events.find(item=>item.id===+e.target.value))}><option value="">New event</option>{events.map(item=><option key={item.id} value={item.id}>{item.title}</option>)}</select></label>
        <form key={editEvent?.id ?? 'new'} onSubmit={e=>void submit(e,'events')}>
          <label>Event title<input name="title" defaultValue={editEvent?.title} required maxLength={120}/></label><label>Category<select name="category" defaultValue={editEvent?.category}>{categories.map(c=><option key={c} value={c}>{levels[c]}</option>)}</select></label>
          <label>Venue<select name="venueId" defaultValue={editEvent?.venue_id} disabled={!!editEvent} required>{venues.map(venue=><option key={venue.id} value={venue.id}>{venue.name}</option>)}</select></label>
          <label>Starts at<input name="startsAt" type="datetime-local" defaultValue={editEvent?localTime(editEvent.starts_at):undefined} required/></label>
          <label>Price in cents<input name="priceCents" defaultValue={editEvent?.price_cents ?? 2500} type="number" min={100} max={10000000} required/></label>
          <button disabled={busy}>{editEvent?'Save event':'Create event'}</button>
        </form>
      </section>
    </div>
    <div className="admin-forms">
      <section className="panel table-wrap"><h2>Recent audit log</h2><table><thead><tr><th>Action</th><th>Entity</th><th>Time</th></tr></thead><tbody>{audit.map(entry=><tr key={entry.id}><td>{entry.action}</td><td>{entry.entity}</td><td>{date(entry.created_at)}</td></tr>)}</tbody></table></section>
      <section className="panel table-wrap"><h2>Notification deliveries</h2>{mail.length===0 && <p>No deliveries yet.</p>}<table><thead><tr><th>Subject</th><th>Mode</th></tr></thead><tbody>{mail.map(entry=><tr key={entry.id}><td>{entry.subject}</td><td>{entry.delivery_mode}</td></tr>)}</tbody></table></section>
    </div>
  </section>;
}

createRoot(document.getElementById('root')!).render(<Boundary><App/></Boundary>);
