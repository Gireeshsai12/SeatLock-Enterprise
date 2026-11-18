export type Session = {accessToken:string;email:string;role:'ADMIN'|'CUSTOMER'};
export type Category = 'CONCERT'|'MOVIE'|'SPORTS'|'CONFERENCE'|'PERFORMANCE';
export type Listing = {id:number;title:string;category:Category;starts_at:string;price_cents:number;venue:string;city:string;venue_id:number;version:number};
export type Seat = {row_number:number;seat_number:number;state:'AVAILABLE'|'HELD'|'BOOKED'};
export type Cart = {id:string;eventId:number;status:string;expiresAt:string;totalCents:number};
export type Order = {id:string;title:string;seats:string;status:string;payment_status:string|null;total_cents:number};
export type Venue = {id:number;name:string;city:string;rows_count:number;seats_per_row:number};
export const categories:Category[] = ['CONCERT','MOVIE','SPORTS','CONFERENCE','PERFORMANCE'];
export const dollars = (cents:number) => new Intl.NumberFormat('en-US',{style:'currency',currency:'USD'}).format(cents / 100);
export const date = (value:string) => new Date(value).toLocaleString('en-US',{month:'short',day:'numeric',hour:'numeric',minute:'2-digit'});

export async function api<T>(path:string, session?:Session, method='GET', body?:unknown):Promise<T> {
  const response = await fetch('/api/v1' + path,{
    method, cache:'no-store',
    headers:{...(body === undefined ? {} : {'Content-Type':'application/json'}),...(session ? {Authorization:'Bearer '+session.accessToken} : {})},
    body:body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    throw new Error(problem.detail ?? `Request failed (${response.status}).`);
  }
  return response.status === 204 ? undefined as T : response.json();
}
export const message = (error:unknown) => error instanceof Error ? error.message : 'The request failed.';
